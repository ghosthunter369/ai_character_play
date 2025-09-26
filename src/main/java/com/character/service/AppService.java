package com.character.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.character.model.dto.app.AppQueryRequest;
import com.character.model.entity.App;
import com.character.model.entity.User;
import com.character.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
* @author lixuewu
* @description 针对表【app(应用表)】的数据库操作Service
* @createDate 2025-09-23 17:22:13
*/
public interface AppService extends IService<App> {
    /**
     *  聊天
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    Flux<String> chat(Long appId, String message, User loginUser);

    /**
     * 首次打开应用，发送开场白
     * @param appId
     * @param loginUser
     * @return
     */

    /**
     *  获取应用信息封装
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 构造应用查询条件
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 获取应用列表封装
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);
}
