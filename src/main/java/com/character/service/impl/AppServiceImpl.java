package com.character.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.character.ai.AiChatService;
import com.character.ai.AiChatServiceFactory;
import com.character.exception.BusinessException;
import com.character.exception.ErrorCode;
import com.character.exception.ThrowUtils;
import com.character.model.dto.app.AppQueryRequest;
import com.character.model.entity.App;
import com.character.model.entity.User;
import com.character.model.enums.ChatHistoryMessageTypeEnum;
import com.character.model.vo.AppVO;
import com.character.model.vo.UserVO;
import com.character.service.AppService;
import com.character.mapper.AppMapper;
import com.character.service.ChatHistoryService;
import com.character.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
* @author lixuewu
* @description 针对表【app(应用表)】的数据库操作Service实现
* @createDate 2025-09-23 17:22:13
*/
@Service
@RequiredArgsConstructor
public class AppServiceImpl extends ServiceImpl<AppMapper, App>
    implements AppService{


    private final ChatHistoryService chatHistoryService;
    private final AiChatServiceFactory aiChatServiceFactory;
    private final UserService userService;
    @Override
    public Flux<String> chat(Long appId, String message, User loginUser) {
        // 1. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 2. 添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 3. 根据 appId 和 userId获取对应的 AI 服务实例
        AiChatService aiService = aiChatServiceFactory.getAiCodeGeneratorService(appId, loginUser.getId());
        // 4. 调用 AI 生成消息（流式）
        Flux<String> messageStream = aiService.generateChatMessageStream(message , appId + "_" + loginUser.getId());
        // 5. 收集AI响应内容并在完成后记录到对话历史
        StringBuilder aiResponseBuilder = new StringBuilder();
        return messageStream
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getAppId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper<App> wrapper = new QueryWrapper<>();
        return wrapper.eq(id != null, "app_id", id)
                .like(StringUtils.isNotBlank(appName), "app_name", appName)
                .like(StringUtils.isNotBlank(cover), "cover", cover)
                .like(StringUtils.isNotBlank(initPrompt), "init_prompt", initPrompt)
                .eq(priority != null, "priority", priority)
                .eq(userId != null, "user_id", userId)
                .orderBy(StringUtils.isNotBlank(sortField), "ascend".equals(sortOrder), sortField);

    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }
}




