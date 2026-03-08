package com.hospital.xray.service;

import com.hospital.xray.dto.AccessTokenVO;
import com.hospital.xray.dto.LoginDTO;
import com.hospital.xray.dto.LoginVO;
import com.hospital.xray.dto.UserInfoVO;

public interface AuthService {

    LoginVO login(LoginDTO loginDTO);

    AccessTokenVO refreshToken(String refreshToken);

    void logout(Long userId);

    UserInfoVO getCurrentUserInfo(Long userId);
}
