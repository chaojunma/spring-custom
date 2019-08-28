package com.mk.controller;

import com.mk.annotation.XAutowired;
import com.mk.annotation.XController;
import com.mk.annotation.XRequestMapping;
import com.mk.annotation.XRequestParam;
import com.mk.service.UserService;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@XController
@XRequestMapping("/user")
public class UserController {

    @XAutowired
    private UserService userService;

    @XRequestMapping("/query")
    public void query(HttpServletRequest req,
                      HttpServletResponse resp,
                      @XRequestParam("name") String name,
                      @XRequestParam("age") Integer age) throws IOException {

        resp.getWriter().write(userService.getUserByName(name));
    }
}
