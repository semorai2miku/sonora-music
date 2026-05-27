package com.sonora.client.controller;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sonora.common.constant.Constants;
import com.sonora.common.result.R;
import com.sonora.common.util.JwtUtil;
import com.sonora.mapper.*;
import com.sonora.model.entity.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Tag(name = "客户端-用户", description = "客户端注册、登录、资料和喜欢的音乐")
@RestController
@RequestMapping("/api/client")
public class ClientUserController {

    private static final String ROLE_USER = "USER";
    private static final String PLAYLIST_TYPE_LIKED = "liked";
    private static final String FAVORITE_TYPE_SONG = "song";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final UserFavoriteMapper userFavoriteMapper;
    private final SongMapper songMapper;
    private final PasswordEncoder passwordEncoder;

    public ClientUserController(UserMapper userMapper,
                                RoleMapper roleMapper,
                                UserRoleMapper userRoleMapper,
                                PlaylistMapper playlistMapper,
                                PlaylistSongMapper playlistSongMapper,
                                UserFavoriteMapper userFavoriteMapper,
                                SongMapper songMapper,
                                PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.playlistMapper = playlistMapper;
        this.playlistSongMapper = playlistSongMapper;
        this.userFavoriteMapper = userFavoriteMapper;
        this.songMapper = songMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "客户端注册")
    @PostMapping("/auth/register")
    @Transactional
    public R<Map<String, Object>> register(@RequestBody AuthRequest body) {
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

        User user = new User();
        user.setUsername(username);
        user.setNickname(username);
        user.setProfileId(generateProfileId());
        user.setPassword(passwordEncoder.encode(body.password()));
        user.setAvatar(Constants.DEFAULT_AVATAR);
        user.setStatus(1);
        user.setGender(0);
        userMapper.insert(user);

        assignClientRole(user.getId());
        ensureLikedPlaylist(user.getId());

        return R.ok(buildAuthData(userMapper.selectById(user.getId())));
    }

    @Operation(summary = "客户端登录")
    @PostMapping("/auth/login")
    public R<Map<String, Object>> login(@RequestBody AuthRequest body) {
        String username = body.username() == null ? "" : body.username().trim();
        if (!StringUtils.hasText(username) || body.password() == null) {
            return R.badRequest("请输入用户名和密码");
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return R.notFound("用户不存在");
        }
        if (Objects.equals(user.getStatus(), 0)) {
            return R.forbidden("账号已被禁用");
        }
        if (!passwordEncoder.matches(body.password(), user.getPassword())) {
            return R.badRequest("密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        ensureLikedPlaylist(user.getId());
        return R.ok(buildAuthData(userMapper.selectById(user.getId())));
    }

    @Operation(summary = "当前用户资料")
    @GetMapping("/auth/me")
    public R<Map<String, Object>> me() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.notFound("用户不存在");
        }
        ensureLikedPlaylist(userId);
        return R.ok(profileOf(user));
    }

    @Operation(summary = "编辑当前用户资料")
    @PutMapping("/auth/me")
    public R<Map<String, Object>> updateMe(@RequestBody ProfileRequest body) {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.notFound("用户不存在");
        }

        if (StringUtils.hasText(body.username())) {
            String username = body.username().trim();
            R<Map<String, Object>> invalid = validateUsername(username);
            if (invalid != null) {
                return invalid;
            }
            if (existsUsername(username, userId)) {
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
            if (existsProfileId(profileId, userId)) {
                return R.badRequest("角色ID已存在");
            }
            user.setProfileId(profileId);
        }
        if (body.avatar() != null) {
            user.setAvatar(StringUtils.hasText(body.avatar()) ? body.avatar().trim() : Constants.DEFAULT_AVATAR);
        }
        if (body.bio() != null) {
            user.setBio(body.bio().trim());
        }
        userMapper.updateById(user);
        return R.ok(profileOf(userMapper.selectById(userId)));
    }

    @Operation(summary = "修改当前用户密码")
    @PutMapping("/auth/password")
    public R<Void> changePassword(@RequestBody PasswordRequest body) {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return R.notFound("用户不存在");
        }
        if (body.oldPassword() == null || body.newPassword() == null) {
            return R.badRequest("请输入原密码和新密码");
        }
        if (!passwordEncoder.matches(body.oldPassword(), user.getPassword())) {
            return R.badRequest("原密码错误");
        }
        if (!validPassword(body.newPassword())) {
            return R.badRequest("新密码长度需为 6-72 位");
        }
        user.setPassword(passwordEncoder.encode(body.newPassword()));
        userMapper.updateById(user);
        return R.ok();
    }

    @Operation(summary = "我的歌单")
    @GetMapping("/me/playlists")
    public R<List<Map<String, Object>>> myPlaylists() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        ensureLikedPlaylist(userId);
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getUserId, userId)
                        .eq(Playlist::getStatus, 1)
                        .orderByDesc(Playlist::getPinned)
                        .orderByAsc(Playlist::getCreatedAt));
        return R.ok(playlists.stream().map(this::playlistOf).toList());
    }

    @Operation(summary = "我的喜欢歌曲 ID")
    @GetMapping("/me/likes/song-ids")
    public R<List<Long>> likedSongIds() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        List<Long> ids = userFavoriteMapper.selectList(
                        new LambdaQueryWrapper<UserFavorite>()
                                .eq(UserFavorite::getUserId, userId)
                                .eq(UserFavorite::getTargetType, FAVORITE_TYPE_SONG)
                                .orderByDesc(UserFavorite::getCreatedAt))
                .stream()
                .map(UserFavorite::getTargetId)
                .toList();
        return R.ok(ids);
    }

    @Operation(summary = "我的喜欢歌曲")
    @GetMapping("/me/likes/songs")
    public R<List<Map<String, Object>>> likedSongs() {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        List<Long> ids = userFavoriteMapper.selectList(
                        new LambdaQueryWrapper<UserFavorite>()
                                .eq(UserFavorite::getUserId, userId)
                                .eq(UserFavorite::getTargetType, FAVORITE_TYPE_SONG)
                                .orderByDesc(UserFavorite::getCreatedAt))
                .stream()
                .map(UserFavorite::getTargetId)
                .toList();
        if (ids.isEmpty()) {
            return R.ok(List.of());
        }
        Map<Long, Song> songMap = songMapper.selectList(
                        new LambdaQueryWrapper<Song>()
                                .in(Song::getId, ids)
                                .eq(Song::getStatus, 1))
                .stream()
                .collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));
        List<Map<String, Object>> songs = new ArrayList<>();
        for (Long id : ids) {
            Song song = songMap.get(id);
            if (song != null) {
                songs.add(songOf(song, true));
            }
        }
        return R.ok(songs);
    }

    @Operation(summary = "喜欢歌曲")
    @PostMapping("/me/likes/songs/{songId}")
    @Transactional
    public R<Map<String, Object>> likeSong(@PathVariable Long songId) {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        Song song = songMapper.selectById(songId);
        if (song == null || Objects.equals(song.getStatus(), 0)) {
            return R.notFound("歌曲不存在");
        }

        if (!existsFavorite(userId, songId)) {
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setTargetType(FAVORITE_TYPE_SONG);
            favorite.setTargetId(songId);
            userFavoriteMapper.insert(favorite);
        }

        Playlist liked = ensureLikedPlaylist(userId);
        if (!existsPlaylistSong(liked.getId(), songId)) {
            PlaylistSong ps = new PlaylistSong();
            ps.setPlaylistId(liked.getId());
            ps.setSongId(songId);
            ps.setSort(nextPlaylistSongSort(liked.getId()));
            playlistSongMapper.insert(ps);
        }
        if (StringUtils.hasText(song.getCover())) {
            liked.setCover(song.getCover());
            playlistMapper.updateById(liked);
        }
        return R.ok(songOf(song, true));
    }

    @Operation(summary = "取消喜欢歌曲")
    @DeleteMapping("/me/likes/songs/{songId}")
    @Transactional
    public R<Void> unlikeSong(@PathVariable Long songId) {
        Long userId = currentUserId();
        if (userId == null) {
            return R.unauthorized("请先登录");
        }
        userFavoriteMapper.delete(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAVORITE_TYPE_SONG)
                        .eq(UserFavorite::getTargetId, songId));
        Playlist liked = ensureLikedPlaylist(userId);
        playlistSongMapper.delete(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, liked.getId())
                        .eq(PlaylistSong::getSongId, songId));
        refreshLikedPlaylistCover(liked);
        return R.ok();
    }

    public record AuthRequest(String username, String password) {}

    public record ProfileRequest(String username, String profileId, String avatar, String bio) {}

    public record PasswordRequest(String oldPassword, String newPassword) {}

    private Map<String, Object> buildAuthData(User user) {
        String accessToken = JwtUtil.createAccessToken(user.getId(), user.getUsername(), List.of(ROLE_USER));
        String refreshToken = JwtUtil.createRefreshToken(user.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", profileOf(user));
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("expires", DateUtil.format(
                new Date(System.currentTimeMillis() + JwtUtil.ACCESS_EXPIRE_MS),
                "yyyy/MM/dd HH:mm:ss"));
        return data;
    }

    private Map<String, Object> profileOf(User user) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("profileId", user.getProfileId());
        data.put("username", user.getUsername());
        data.put("nickname", StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
        data.put("avatarUrl", StringUtils.hasText(user.getAvatar()) ? user.getAvatar() : Constants.DEFAULT_AVATAR);
        data.put("bio", user.getBio());
        data.put("status", user.getStatus());
        return data;
    }

    private Map<String, Object> playlistOf(Playlist playlist) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", playlist.getId());
        data.put("name", playlist.getName());
        data.put("cover", playlist.getCover());
        data.put("type", playlist.getType());
        data.put("pinned", playlist.getPinned());
        data.put("description", playlist.getDescription());
        data.put("createdAt", playlist.getCreatedAt());
        return data;
    }

    private Map<String, Object> songOf(Song song, boolean liked) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", song.getId());
        data.put("name", song.getName());
        data.put("dt", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        data.put("duration", song.getDuration() == null ? 0 : song.getDuration() * 1000);
        data.put("cover", song.getCover());
        data.put("liked", liked);
        data.put("al", Map.of(
                "id", song.getAlbumId() == null ? 0 : song.getAlbumId(),
                "name", "",
                "picUrl", song.getCover() == null ? "" : song.getCover()));
        data.put("ar", List.of(Map.of(
                "id", 0,
                "name", song.getArtistIds() == null ? "" : song.getArtistIds())));
        return data;
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

    private boolean existsFavorite(Long userId, Long songId) {
        return userFavoriteMapper.selectCount(
                new LambdaQueryWrapper<UserFavorite>()
                        .eq(UserFavorite::getUserId, userId)
                        .eq(UserFavorite::getTargetType, FAVORITE_TYPE_SONG)
                        .eq(UserFavorite::getTargetId, songId)) > 0;
    }

    private boolean existsPlaylistSong(Long playlistId, Long songId) {
        return playlistSongMapper.selectCount(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, songId)) > 0;
    }

    private int nextPlaylistSongSort(Long playlistId) {
        return Math.toIntExact(playlistSongMapper.selectCount(
                new LambdaQueryWrapper<PlaylistSong>().eq(PlaylistSong::getPlaylistId, playlistId))) + 1;
    }

    private void refreshLikedPlaylistCover(Playlist playlist) {
        List<PlaylistSong> songs = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlist.getId())
                        .orderByDesc(PlaylistSong::getSort)
                        .last("LIMIT 1"));
        if (songs.isEmpty()) {
            playlist.setCover(null);
            playlistMapper.updateById(playlist);
            return;
        }
        Song lastSong = songMapper.selectById(songs.get(0).getSongId());
        playlist.setCover(lastSong == null ? null : lastSong.getCover());
        playlistMapper.updateById(playlist);
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

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Long id) {
            return id;
        }
        try {
            return Long.valueOf(String.valueOf(principal));
        } catch (Exception e) {
            return null;
        }
    }
}
