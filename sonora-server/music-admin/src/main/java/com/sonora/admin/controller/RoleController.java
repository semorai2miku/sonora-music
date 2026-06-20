package com.sonora.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sonora.common.result.R;
import com.sonora.mapper.PermissionMapper;
import com.sonora.mapper.RoleMapper;
import com.sonora.mapper.RolePermissionMapper;
import com.sonora.mapper.UserRoleMapper;
import com.sonora.model.entity.Permission;
import com.sonora.model.entity.Role;
import com.sonora.model.entity.RolePermission;
import com.sonora.model.entity.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Tag(name = "管理端-角色管理", description = "后台登录角色与菜单权限管理")
@RestController
@RequestMapping("/api/admin/roles")
public class RoleController {

    private static final Set<String> PROTECTED_ROLE_CODES = Set.of("ADMIN", "USER");
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public RoleController(RoleMapper roleMapper,
                          PermissionMapper permissionMapper,
                          RolePermissionMapper rolePermissionMapper,
                          UserRoleMapper userRoleMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Operation(summary = "分页查询后台角色")
    @GetMapping
    public R<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>()
                .and(StringUtils.hasText(keyword), query -> query
                        .like(Role::getName, keyword.trim())
                        .or()
                        .like(Role::getCode, keyword.trim()))
                .eq(status != null, Role::getStatus, status)
                .orderByAsc(Role::getSort)
                .orderByAsc(Role::getId);
        Page<Role> page = roleMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);

        List<Long> roleIds = page.getRecords().stream().map(Role::getId).toList();
        Map<Long, Long> userCounts = countByRole(userRoleMapper.selectMaps(
                new QueryWrapper<UserRole>()
                        .select("role_id", "COUNT(*) AS item_count")
                        .in(!roleIds.isEmpty(), "role_id", roleIds)
                        .apply(roleIds.isEmpty(), "1 = 0")
                        .groupBy("role_id")));
        Map<Long, Long> permissionCounts = countByRole(rolePermissionMapper.selectMaps(
                new QueryWrapper<RolePermission>()
                        .select("role_id", "COUNT(*) AS item_count")
                        .in(!roleIds.isEmpty(), "role_id", roleIds)
                        .apply(roleIds.isEmpty(), "1 = 0")
                        .groupBy("role_id")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", page.getRecords().stream()
                .map(role -> roleOf(role, userCounts, permissionCounts))
                .toList());
        data.put("total", page.getTotal());
        data.put("pageNum", safePageNum);
        data.put("pageSize", safePageSize);
        return R.ok(data);
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return R.notFound("角色不存在");
        }
        long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        long permissionCount = rolePermissionMapper.selectCount(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        return R.ok(roleOf(role, Map.of(id, userCount), Map.of(id, permissionCount)));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    public R<Map<String, Object>> create(@RequestBody RoleRequest body) {
        String code = normalizeCode(body.code());
        String name = normalizeName(body.name());
        R<Map<String, Object>> invalid = validateRole(code, name, body.status());
        if (invalid != null) {
            return invalid;
        }
        if (existsCode(code, null)) {
            return R.badRequest("角色编码已存在");
        }

        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(trimToNull(body.description()));
        role.setSort(normalizeSort(body.sort()));
        role.setStatus(body.status() == null ? 1 : body.status());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException e) {
            return R.badRequest("角色编码已存在");
        }
        return detail(role.getId());
    }

    @Operation(summary = "编辑角色")
    @PutMapping("/{id}")
    public R<Map<String, Object>> update(@PathVariable Long id, @RequestBody RoleRequest body) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return R.notFound("角色不存在");
        }
        String code = normalizeCode(body.code());
        String name = normalizeName(body.name());
        R<Map<String, Object>> invalid = validateRole(code, name, body.status());
        if (invalid != null) {
            return invalid;
        }
        if (isProtected(role) && !Objects.equals(role.getCode(), code)) {
            return R.forbidden("系统内置角色不允许修改编码");
        }
        if (isProtected(role) && body.status() != null
                && !Objects.equals(role.getStatus(), body.status())) {
            return R.forbidden("系统内置角色不允许切换状态");
        }
        if (existsCode(code, id)) {
            return R.badRequest("角色编码已存在");
        }

        role.setCode(code);
        role.setName(name);
        role.setDescription(trimToNull(body.description()));
        role.setSort(normalizeSort(body.sort()));
        if (body.status() != null) {
            role.setStatus(body.status());
        }
        roleMapper.updateById(role);
        return detail(id);
    }

    @Operation(summary = "启用或禁用角色")
    @PutMapping("/{id}/status")
    public R<Map<String, Object>> updateStatus(@PathVariable Long id, @RequestParam int status) {
        if (status != 0 && status != 1) {
            return R.badRequest("状态值只能是 0 或 1");
        }
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return R.notFound("角色不存在");
        }
        if (isProtected(role)) {
            return R.forbidden("系统内置角色不允许切换状态");
        }
        role.setStatus(status);
        roleMapper.updateById(role);
        return detail(id);
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    @Transactional
    public R<Void> delete(@PathVariable Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return R.notFound("角色不存在");
        }
        if (isProtected(role)) {
            return R.forbidden("系统内置角色不允许删除");
        }
        long userCount = userRoleMapper.selectCount(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getRoleId, id));
        if (userCount > 0) {
            return R.badRequest("该角色仍关联 " + userCount + " 个账号，请先解除关联");
        }
        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        roleMapper.deleteById(id);
        return R.ok();
    }

    @Operation(summary = "获取菜单权限树")
    @GetMapping("/permission-tree")
    public R<List<Map<String, Object>>> permissionTree() {
        List<Permission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>()
                        .orderByAsc(Permission::getSort)
                        .orderByAsc(Permission::getId));
        return R.ok(buildPermissionTree(permissions, 0L));
    }

    @Operation(summary = "获取角色权限 ID")
    @GetMapping("/{id}/permissions")
    public R<List<Long>> permissions(@PathVariable Long id) {
        if (roleMapper.selectById(id) == null) {
            return R.notFound("角色不存在");
        }
        List<Long> permissionIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>()
                                .eq(RolePermission::getRoleId, id))
                .stream()
                .map(RolePermission::getPermissionId)
                .toList();
        return R.ok(permissionIds);
    }

    @Operation(summary = "分配角色菜单权限")
    @PutMapping("/{id}/permissions")
    @Transactional
    public R<List<Long>> assignPermissions(@PathVariable Long id,
                                           @RequestBody PermissionRequest body) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return R.notFound("角色不存在");
        }
        if (PROTECTED_ROLE_CODES.contains(role.getCode())) {
            return R.forbidden("该系统内置角色的权限不允许修改");
        }

        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        if (body != null && body.permissionIds() != null) {
            body.permissionIds().stream().filter(Objects::nonNull).forEach(requestedIds::add);
        }
        List<Permission> selected = requestedIds.isEmpty()
                ? List.of()
                : permissionMapper.selectBatchIds(requestedIds);
        if (selected.size() != requestedIds.size()) {
            return R.badRequest("包含不存在的权限项，请刷新后重试");
        }

        Map<Long, Permission> allPermissions = new HashMap<>();
        permissionMapper.selectList(null).forEach(permission -> allPermissions.put(permission.getId(), permission));
        LinkedHashSet<Long> normalizedIds = new LinkedHashSet<>(requestedIds);
        for (Permission permission : selected) {
            appendAncestors(permission, allPermissions, normalizedIds);
        }

        rolePermissionMapper.delete(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, id));
        for (Long permissionId : normalizedIds) {
            RolePermission link = new RolePermission();
            link.setRoleId(id);
            link.setPermissionId(permissionId);
            rolePermissionMapper.insert(link);
        }
        return R.ok(new ArrayList<>(normalizedIds));
    }

    private Map<String, Object> roleOf(Role role,
                                       Map<Long, Long> userCounts,
                                       Map<Long, Long> permissionCounts) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", role.getId());
        item.put("code", role.getCode());
        item.put("name", role.getName());
        item.put("description", role.getDescription());
        item.put("sort", role.getSort());
        item.put("status", role.getStatus());
        item.put("userCount", userCounts.getOrDefault(role.getId(), 0L));
        item.put("permissionCount", permissionCounts.getOrDefault(role.getId(), 0L));
        item.put("protectedRole", isProtected(role));
        item.put("permissionsEditable", !PROTECTED_ROLE_CODES.contains(role.getCode()));
        item.put("createdAt", role.getCreatedAt());
        item.put("updatedAt", role.getUpdatedAt());
        return item;
    }

    private Map<Long, Long> countByRole(List<Map<String, Object>> rows) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object roleId = row.get("role_id");
            Object count = row.get("item_count");
            if (roleId instanceof Number roleNumber && count instanceof Number countNumber) {
                result.put(roleNumber.longValue(), countNumber.longValue());
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildPermissionTree(List<Permission> permissions, Long parentId) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Permission permission : permissions) {
            if (!Objects.equals(permission.getParentId(), parentId)) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", permission.getId());
            node.put("label", permission.getName());
            node.put("code", permission.getCode());
            node.put("type", permission.getType());
            node.put("visible", permission.getVisible());
            node.put("children", buildPermissionTree(permissions, permission.getId()));
            nodes.add(node);
        }
        return nodes;
    }

    private void appendAncestors(Permission permission,
                                 Map<Long, Permission> permissions,
                                 Set<Long> selectedIds) {
        Long parentId = permission.getParentId();
        Set<Long> visited = new LinkedHashSet<>();
        while (parentId != null && parentId > 0 && visited.add(parentId)) {
            Permission parent = permissions.get(parentId);
            if (parent == null) {
                break;
            }
            selectedIds.add(parentId);
            parentId = parent.getParentId();
        }
    }

    private R<Map<String, Object>> validateRole(String code, String name, Integer status) {
        if (!ROLE_CODE_PATTERN.matcher(code).matches()) {
            return R.badRequest("角色编码需为 2-64 位大写字母、数字或下划线，且以字母开头");
        }
        if (!StringUtils.hasText(name) || name.length() > 64) {
            return R.badRequest("角色名称不能为空且不能超过 64 个字符");
        }
        if (status != null && status != 0 && status != 1) {
            return R.badRequest("状态值只能是 0 或 1");
        }
        return null;
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<Role>().eq(Role::getCode, code);
        if (excludeId != null) {
            wrapper.ne(Role::getId, excludeId);
        }
        return roleMapper.selectCount(wrapper) > 0;
    }

    private boolean isProtected(Role role) {
        return role != null && PROTECTED_ROLE_CODES.contains(role.getCode());
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int normalizeSort(Integer sort) {
        return sort == null ? 0 : Math.max(0, sort);
    }

    public record RoleRequest(String code,
                              String name,
                              String description,
                              Integer sort,
                              Integer status) {
    }

    public record PermissionRequest(List<Long> permissionIds) {
    }
}
