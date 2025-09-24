package com.character.service;

import com.character.model.entity.App;
import com.baomidou.mybatisplus.extension.service.IService;
import com.character.model.entity.User;
import reactor.core.publisher.Flux;

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
}
