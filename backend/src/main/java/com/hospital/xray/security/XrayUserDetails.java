package com.hospital.xray.security;

import com.hospital.xray.entity.SysUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class XrayUserDetails implements UserDetails {

    private final SysUser sysUser;
    private final String roleCode;

    public XrayUserDetails(SysUser sysUser, String roleCode) {
        this.sysUser = sysUser;
        this.roleCode = roleCode;
    }

    public Long getUserId() {
        return sysUser.getUserId();
    }

    public String getRealName() {
        return sysUser.getRealName();
    }

    public String getDepartment() {
        return sysUser.getDepartment();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(roleCode));
    }

    @Override
    public String getPassword() {
        return sysUser.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return sysUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return sysUser.getStatus() == 1;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return sysUser.getStatus() == 1;
    }
}
