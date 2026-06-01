package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.constant.Constants;
import com.sonora.common.exception.BusinessException;
import com.sonora.common.result.R;
import com.sonora.file.service.MinioService;
import com.sonora.mapper.*;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

@Tag(name = "管理端-客户端用户管理", description = "管理客户端注册用户")
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    private static final String ROLE_USER = "USER";
    private static final String PLAYLIST_TYPE_LIKED = "liked";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[\\x21-\\x7E]{6,72}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final CommentMapper commentMapper;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;

    public UserController(UserMapper userMapper,
                          RoleMapper roleMapper,
                          UserRoleMapper userRoleMapper,
                          PlaylistMapper playlistMapper,
                          PlaylistSongMapper playlistSongMapper,
                          UserFavoriteMapper userFavoriteMapper,
                          CommentMapper commentMapper,
                          PasswordEncoder passwordEncoder,
                          MinioService minioService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.commentMapper = commentMapper;
        this.passwordEncoder = passwordEncoder;
        this.minioService = minioService;
    }

    @Operation(summary = "分页查询客户端用户")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) Integer status) {
        Page<User> page = userMapper.selectClientUserPage(new Page<>(pageNum, pageSize), username, email, phone, status);
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
            return R.badRequest("密码需为 6-72 位，且只能包含字母、数字和特殊符号");
        }
        String email = body.email() == null ? "" : body.email().trim();
        if (!validEmail(email)) {
            return R.badRequest("请输入正确的邮箱");
        }
        if (existsUsername(username, null)) {
            return R.badRequest("用户名已存在");
        }
        if (existsEmail(email, null)) {
            return R.badRequest("邮箱已存在");
        }
        String phone = StringUtils.hasText(body.phone()) ? normalizePhone(body.phone()) : null;
        if (StringUtils.hasText(body.phone()) && phone == null) {
            return R.badRequest("手机号格式不正确");
        }
        String profileId = StringUtils.hasText(body.profileId()) ? body.profileId().trim() : generateProfileId();
        if (!validProfileId(profileId)) {
            return R.badRequest("角色ID仅支持 4-32 位字母、数字、下划线和短横线");
        }
        if (existsProfileId(profileId, null)) {
            return R.badRequest("角色ID已存在");
        }
        userMapper.purgeDeletedIdentity(username, email, profileId);

        User user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setProfileId(profileId);
        user.setPassword(passwordEncoder.encode(body.password()));
        user.setEmail(email);
        user.setPhone(phone);
        user.setAvatar(StringUtils.hasText(body.avatar())
                ? minioService.normalizeForStorage(body.avatar().trim())
                : Constants.DEFAULT_AVATAR);
        user.setBio(normalizeBio(body.bio()));
        user.setGender(0);
        user.setStatus(body.status() == null ? 1 : body.status());
        try {
            userMapper.insert(user);
            assignClientRole(user.getId());
            ensureLikedPlaylist(user.getId());
            return R.ok(userOf(userMapper.selectById(user.getId())));
        } catch (DataAccessException e) {
            throw friendlyUserWriteException("新增用户", e);
        }
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
                return R.badRequest("密码需为 6-72 位，且只能包含字母、数字和特殊符号");
            }
            user.setPassword(passwordEncoder.encode(body.password()));
        }
        if (body.email() != null) {
            String email = body.email().trim();
            if (!validEmail(email)) {
                return R.badRequest("请输入正确的邮箱");
            }
            if (existsEmail(email, id)) {
                return R.badRequest("邮箱已存在");
            }
            user.setEmail(email);
        }
        if (body.phone() != null) {
            String phone = normalizePhone(body.phone());
            if (StringUtils.hasText(body.phone()) && phone == null) {
                return R.badRequest("手机号格式不正确");
            }
            user.setPhone(phone);
        }
        if (body.avatar() != null) {
            user.setAvatar(StringUtils.hasText(body.avatar())
                    ? minioService.normalizeForStorage(body.avatar().trim())
                    : Constants.DEFAULT_AVATAR);
        }
        if (body.bio() != null) {
            user.setBio(normalizeBio(body.bio()));
        }
        if (body.status() != null) {
            user.setStatus(body.status());
        }
        try {
            userMapper.updateById(user);
            return R.ok(userOf(userMapper.selectById(id)));
        } catch (DataAccessException e) {
            throw friendlyUserWriteException("修改用户", e);
        }
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
        deleteClientUserData(id);
        return R.ok();
    }

    @Operation(summary = "批量删除客户端用户")
    @PostMapping("/batch-delete")
    @Transactional
    public R<Map<String, Object>> batchDelete(@RequestBody BatchDeleteRequest body) {
        if (body == null || body.ids() == null || body.ids().isEmpty()) {
            return R.badRequest("请选择要删除的用户");
        }
        List<Map<String, Object>> deletedUsers = new ArrayList<>();
        for (Long id : body.ids().stream().filter(Objects::nonNull).distinct().toList()) {
            User user = userMapper.selectById(id);
            if (user == null || !isClientUser(id)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("username", user.getUsername());
            deleteClientUserData(id);
            deletedUsers.add(item);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", deletedUsers.size());
        data.put("deleted", deletedUsers);
        return R.ok(data);
    }

    public record UserPayload(
            String username,
            String password,
            String profileId,
            String email,
            String phone,
            String avatar,
            String bio,
            Integer status) {}

    public record BatchDeleteRequest(List<Long> ids) {}

    private Map<String, Object> userOf(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", user.getId());
        data.put("profileId", user.getProfileId());
        data.put("username", user.getUsername());
        data.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("avatar", minioService.resolvePreviewUrl(
                StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : Constants.DEFAULT_AVATAR));
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
        playlist.setStatus(0);
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
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }

    private boolean validEmail(String email) {
        return StringUtils.hasText(email) && email.length() <= 128 && EMAIL_PATTERN.matcher(email).matches();
    }

    private String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.trim();
        return PHONE_PATTERN.matcher(value).matches() ? value : null;
    }

    private String normalizeBio(String bio) {
        if (bio == null || bio.isBlank()) {
            return null;
        }
        return bio.trim().length() > 512 ? bio.trim().substring(0, 512) : bio.trim();
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

    private boolean existsEmail(String email, Long excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>().eq(User::getEmail, email);
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

    private void deleteClientUserData(Long id) {
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>().eq(Playlist::getUserId, id));
        List<Long> playlistIds = playlists.stream().map(Playlist::getId).toList();
        if (!playlistIds.isEmpty()) {
            playlistSongMapper.delete(
                    new LambdaQueryWrapper<PlaylistSong>().in(PlaylistSong::getPlaylistId, playlistIds));
        }
        playlistMapper.delete(new LambdaQueryWrapper<Playlist>().eq(Playlist::getUserId, id));
        userFavoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>().eq(UserFavorite::getUserId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getUserId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        userMapper.hardDeleteById(id);
    }

    private BusinessException friendlyUserWriteException(String action, DataAccessException e) {
        if (e instanceof DuplicateKeyException) {
            return new BusinessException(400, "用户名、邮箱或角色ID已存在");
        }
        String message = rootCauseMessage(e);
        if (message.contains("t_playlist") || message.contains("Unknown column 'type'")
                || message.contains("Unknown column 'pinned'")) {
            return new BusinessException(500, action + "失败：开发库的 t_playlist 表结构未更新，请先执行歌单字段迁移 SQL");
        }
        if (message.contains("Unknown column 'email'") || message.contains("Field 'email'")) {
            return new BusinessException(500, action + "失败：开发库的 sys_user.email 字段未更新，请先执行用户邮箱字段迁移 SQL");
        }
        return new BusinessException(500, action + "失败：" + message);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage() == null ? "数据库写入异常" : root.getMessage();
    }
}
