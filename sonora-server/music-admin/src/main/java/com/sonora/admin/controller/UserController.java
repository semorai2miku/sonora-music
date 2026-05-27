package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.mapper.*;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;

@Tag(name = "管理端-客户端用户管理", description = "管理客户端注册用户")
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private static final String ROLE_USER = "USER";
    private static final String PLAYLIST_TYPE_LIKED = "liked";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserMapper userMapper,
                          RoleMapper roleMapper,
                          UserRoleMapper userRoleMapper,
                          PlaylistMapper playlistMapper,
                          PlaylistSongMapper playlistSongMapper,
                          UserFavoriteMapper userFavoriteMapper,
                          PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "分页查询客户端用户")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        Page<User> page = userMapper.selectClientUserPage(new Page<>(pageNum, pageSize), keyword, status);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords().stream().map(this::userOf).toList());
        data.put("total", page.getTotal());
        data.put("pageNum", pageNum);
        data.put("pageSize", pageSize);
        return R.ok(data);
    }

    @Operation(summary = "客户端用户详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null || !isClientUser(id)) {
            return R.notFound("用户不存在");
        }
        return R.ok(userOf(user));
    }

    @Operation(summary = "新增客户端用户")
    @PostMapping
    @Transactional
    public R<Map<String, Object>> create(@RequestBody UserPayload body) {
        String username = body.username() == null ? "" : body.username().trim();
        R<Map<String, Object>> invalid = validateUsername(username);
        if (invalid != null) {
            return invalid;
        }
        if (!validPassword(body.password())) {
            return R.badRequest("密码长度需为 6-72 位");
        }
        if (existsUsername(username, null)) {
            return R.badRequest("用户名已存在");
        }
        String profileId = StringUtils.hasText(body.profileId()) ? body.profileId().trim() : generateProfileId();
        if (!validProfileId(profileId)) {
            return R.badRequest("角色ID仅支持 4-32 位字母、数字、下划线和短横线");
        }
        if (existsProfileId(profileId, null)) {
            return R.badRequest("角色ID已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setProfileId(profileId);
        user.setPassword(passwordEncoder.encode(body.password()));
        user.setAvatar(StringUtils.hasText(body.avatar()) ? body.avatar().trim() : Constants.DEFAULT_AVATAR);
        user.setBio(body.bio());
        user.setGender(0);
        user.setStatus(body.status() == null ? 1 : body.status());
        userMapper.insert(user);

        assignClientRole(user.getId());
        ensureLikedPlaylist(user.getId());
        return R.ok(userOf(userMapper.selectById(user.getId())));
    }

    @Operation(summary = "编辑客户端用户")
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody UserPayload body) {
        User user = userMapper.selectById(id);
        if (user == null || !isClientUser(id)) {
            return R.notFound("用户不存在");
        }
        if (StringUtils.hasText(body.username())) {
            String username = body.username().trim();
            R<Map<String, Object>> invalid = validateUsername(username);
            if (invalid != null) {
                return invalid;
            }
            if (existsUsername(username, id)) {
                return R.badRequest("用户名已存在");
            }
            user.setUsername(username);
            user.setNickname(username);
        }
        if (StringUtils.hasText(body.profileId())) {
            String profileId = body.profileId().trim();
            if (!validProfileId(profileId)) {
                return R.badRequest("角色ID仅支持 4-32 位字母、数字、下划线和短横线");
            }
            if (existsProfileId(profileId, id)) {
                return R.badRequest("角色ID已存在");
            }
            user.setProfileId(profileId);
        }
        if (body.password() != null && !body.password().isBlank()) {
            if (!validPassword(body.password())) {
                return R.badRequest("密码长度需为 6-72 位");
            }
            user.setPassword(passwordEncoder.encode(body.password()));
        }
        if (body.avatar() != null) {
            user.setAvatar(StringUtils.hasText(body.avatar()) ? body.avatar().trim() : Constants.DEFAULT_AVATAR);
        }
        if (body.bio() != null) {
            user.setBio(body.bio().trim());
        }
        if (body.status() != null) {
            user.setStatus(body.status());
        }
        userMapper.updateById(user);
        return R.ok(userOf(userMapper.selectById(id)));
    }

    @Operation(summary = "启用/禁用客户端用户")
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam int status) {
        User user = userMapper.selectById(id);
        if (user == null || !isClientUser(id)) {
            return R.notFound("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        return R.ok(userOf(userMapper.selectById(id)));
    }

    @Operation(summary = "删除客户端用户")
    @DeleteMapping("/{id}")
    @Transactional
    public R<Void> delete(@PathVariable Long id) {
        if (!isClientUser(id)) {
            return R.notFound("用户不存在");
        }
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>().eq(Playlist::getUserId, id));
        List<Long> playlistIds = playlists.stream().map(Playlist::getId).toList();
        if (!playlistIds.isEmpty()) {
            playlistSongMapper.delete(
                    new LambdaQueryWrapper<PlaylistSong>().in(PlaylistSong::getPlaylistId, playlistIds));
        }
        playlistMapper.delete(new LambdaQueryWrapper<Playlist>().eq(Playlist::getUserId, id));
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userMapper.deleteById(id);
        return R.ok();
    }

    public record UserPayload(
            String username,
            String password,
            String profileId,
            String avatar,
            String bio,
            Integer status) {}

    private Map<String, Object> userOf(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("profileId", user.getProfileId());
        data.put("username", user.getUsername());
        data.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        data.put("avatar", StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : Constants.DEFAULT_AVATAR);
        data.put("bio", user.getBio());
        data.put("status", user.getStatus());
        data.put("lastLoginAt", user.getLastLoginAt());
        data.put("createdAt", user.getCreatedAt());
        data.put("updatedAt", user.getUpdatedAt());
        return data;
    }

    private void assignClientRole(Long userId) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, ROLE_USER).last("LIMIT 1"));
        if (role == null) {
            return;
        }
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    private boolean isClientUser(Long userId) {
        return userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>()
                        .eq(UserRole::getUserId, userId)
                        .inSql(UserRole::getRoleId, "SELECT id FROM sys_role WHERE code = 'USER'")) > 0;
    }

    private Playlist ensureLikedPlaylist(Long userId) {
        Playlist playlist = playlistMapper.selectOne(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getUserId, userId)
                        .eq(Playlist::getType, PLAYLIST_TYPE_LIKED)
                        .last("LIMIT 1"));
        if (playlist != null) {
            return playlist;
        }
        playlist = new Playlist();
        playlist.setName("我喜欢的音乐");
        playlist.setUserId(userId);
        playlist.setType(PLAYLIST_TYPE_LIKED);
        playlist.setPinned(1);
        playlist.setDescription("自动收藏你点红心的歌曲");
        playlist.setPlayCount(0L);
        playlist.setCollectCount(0L);
        playlist.setStatus(1);
        playlistMapper.insert(playlist);
        return playlist;
    }

    private String generateProfileId() {
        for (int i = 0; i < 20; i++) {
            String candidate = "S" + Long.toUnsignedString(RANDOM.nextLong(), 36).toUpperCase(Locale.ROOT);
            candidate = candidate.length() > 12 ? candidate.substring(0, 12) : candidate;
            if (!existsProfileId(candidate, null)) {
                return candidate;
            }
        }
        return "S" + System.currentTimeMillis();
    }

    private R<Map<String, Object>> validateUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return R.badRequest("请输入用户名");
        }
        if (username.length() < 2 || username.length() > 32) {
            return R.badRequest("用户名长度需为 2-32 位");
        }
        if (username.codePoints().anyMatch(Character::isISOControl)) {
            return R.badRequest("用户名不能包含控制字符");
        }
        return null;
    }

    private boolean validPassword(String password) {
        return password != null && password.length() >= 6 && password.length() <= 72;
    }

    private boolean validProfileId(String profileId) {
        return profileId != null && profileId.matches("[A-Za-z0-9_-]{4,32}");
    }

    private boolean existsUsername(String username, Long excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getUsername, username);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    private boolean existsProfileId(String profileId, Long excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getProfileId, profileId);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }
}
