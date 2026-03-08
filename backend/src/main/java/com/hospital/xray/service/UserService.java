package com.hospital.xray.service;

import com.hospital.xray.dto.*;

public interface UserService {

    PageResult<UserVO> listUsers(UserQueryDTO queryDTO);

    UserVO getUserById(Long userId);

    Long createUser(UserCreateDTO createDTO);

    void updateUser(Long userId, UserUpdateDTO updateDTO);

    void updatePassword(Long userId, PasswordUpdateDTO passwordUpdateDTO, Long operatorId, boolean isAdmin);

    void updateStatus(Long userId, Integer status);

    void deleteUser(Long userId);
}
