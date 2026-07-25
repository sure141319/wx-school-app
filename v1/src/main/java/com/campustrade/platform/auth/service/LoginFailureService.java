package com.campustrade.platform.auth.service;

import com.campustrade.platform.common.time.BeijingTime;
import com.campustrade.platform.config.AppProperties;
import com.campustrade.platform.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class LoginFailureService {

    private final UserMapper userMapper;
    private final AppProperties appProperties;

    public LoginFailureService(UserMapper userMapper, AppProperties appProperties) {
        this.userMapper = userMapper;
        this.appProperties = appProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long userId, int currentFailCount, LocalDateTime currentLockedUntil) {
        int nextFailCount = currentFailCount + 1;
        LocalDateTime lockedUntil = currentLockedUntil;
        if (nextFailCount >= appProperties.getAuth().getMaxLoginFailures()) {
            lockedUntil = BeijingTime.now().plusMinutes(appProperties.getAuth().getLockMinutes());
            nextFailCount = 0;
        }
        userMapper.updateAuthState(userId, nextFailCount, lockedUntil);
    }
}
