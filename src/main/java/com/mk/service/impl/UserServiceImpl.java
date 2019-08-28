package com.mk.service.impl;

import com.mk.annotation.XService;
import com.mk.service.UserService;

@XService
public class UserServiceImpl implements UserService {

    public String getUserByName(String name) {
        return "My name is " + name;
    }
}
