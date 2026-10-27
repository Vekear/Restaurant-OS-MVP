package com.restaurant.user.adapter;

import com.restaurant.contract.UserLookupPort;
import com.restaurant.user.User;
import com.restaurant.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link UserLookupPort} 的用户域实现。 */
@Component
@RequiredArgsConstructor
public class UserLookupAdapter implements UserLookupPort {

    private final UserMapper userMapper;

    @Override
    public String nickname(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? null : user.getNickname();
    }
}
