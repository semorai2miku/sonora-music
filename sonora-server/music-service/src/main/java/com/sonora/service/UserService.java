package com.sonora.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sonora.model.entity.User;

public interface UserService extends IService<User> {

    Page<User> pageUsers(int pageNum, int pageSize, String keyword);
}
