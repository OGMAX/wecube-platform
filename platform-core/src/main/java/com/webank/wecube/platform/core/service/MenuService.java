package com.webank.wecube.platform.core.service;

import com.google.common.collect.Lists;
import com.webank.wecube.platform.core.commons.AuthenticationContextHolder;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.domain.MenuItem;
import com.webank.wecube.platform.core.domain.plugin.PluginPackageMenu;
import com.webank.wecube.platform.core.dto.MenuItemDto;
import com.webank.wecube.platform.core.jpa.MenuItemRepository;
import com.webank.wecube.platform.core.jpa.PluginPackageMenuRepository;
import com.webank.wecube.platform.core.service.plugin.PluginPackageService;
import com.webank.wecube.platform.core.service.user.RoleMenuServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class MenuService {

    @Autowired
    MenuItemRepository menuItemRepository;

    @Autowired
    PluginPackageService pluginPackageService;

    @Autowired
    private PluginPackageMenuRepository pluginPackageMenuRepository;

    @Autowired
    private RoleMenuServiceImpl roleMenuService;

    public List<MenuItem> getAllSysMenuItems() {
        return Lists.newArrayList(menuItemRepository.findAll());
    }

    public List<MenuItemDto> getAllSysMenus() {
        List<MenuItemDto> returnMenuDto = new ArrayList<>();
        Iterable<MenuItem> systemMenus = menuItemRepository.findAll();
        for (MenuItem systemMenu : systemMenus) {
            MenuItemDto systemMenuDto = MenuItemDto.fromSystemMenuItem(systemMenu);
            returnMenuDto.add(systemMenuDto);
        }
        return returnMenuDto;
    }


    public List<MenuItemDto> getAllMenus() {
        List<MenuItemDto> returnMenuDto;

        List<MenuItemDto> allSysMenus = getAllSysMenus();
        returnMenuDto = new ArrayList<>(allSysMenus);

        Optional<List<PluginPackageMenu>> optionalPluginPackageMenus = pluginPackageMenuRepository.findAndMergePluginMenus();
        optionalPluginPackageMenus.ifPresent(pluginPackageMenus -> returnMenuDto.addAll(packageMenuToMenuItemDto(pluginPackageMenus)));
        Collections.sort(returnMenuDto);
        return returnMenuDto;
    }

    public List<MenuItemDto> getCurrentUserAllMenus() throws WecubeCoreException {
        List<MenuItemDto> result = getAllSysMenus();
        // find all distinct current user's own menu codes
        Set<String> currentUserRoles = AuthenticationContextHolder.getCurrentUserRoles();
        if (!CollectionUtils.isEmpty(currentUserRoles)) {
            List<String> currentUserMenuCodeList;
            Set<String> currentUserMenuCodeSet = new HashSet<>();
            for (String userRole : currentUserRoles) {
                currentUserMenuCodeSet.addAll(roleMenuService.getMenuCodeListByRoleName(userRole));
            }
            currentUserMenuCodeList = new ArrayList<>(currentUserMenuCodeSet);
            log.info(String.format("Current user's all menuCode list is: [%s]", currentUserMenuCodeList));

            // filter all packageMenu which has menuCode in current user's own menu code
            List<MenuItemDto> packageMenuDto = new ArrayList<>();
            for (String menuCode : currentUserMenuCodeList) {
                Optional<List<PluginPackageMenu>> foundPackageMenuByCode = this.pluginPackageMenuRepository.findAllActiveMenuByCode(menuCode);
                foundPackageMenuByCode.ifPresent(pluginPackageMenus -> packageMenuDto.addAll(packageMenuToMenuItemDto(pluginPackageMenus)));
            }

            // append packageMenu and sysMenu
            result.addAll(packageMenuDto);
            Collections.sort(result);
            log.info(String.format("Found menuCode: [%s] from both system and plugin package menu database.", result.stream().map(MenuItemDto::getCode).collect(Collectors.toList()).toString()));
        }
        return result;
    }

    public List<PluginPackageMenu> sortPluginPackageMenusById(Set<PluginPackageMenu> packageMenus) {
        List<PluginPackageMenu> packageMenusList = new ArrayList<>(packageMenus);
        Collections.sort(packageMenusList);
        return packageMenusList;
    }


    private List<MenuItemDto> packageMenuToMenuItemDto(List<PluginPackageMenu> pluginPackageMenus) {
        List<MenuItemDto> result = new ArrayList<>();
        pluginPackageMenus.forEach(packageMenu -> {
            MenuItem menuItem = menuItemRepository.findByCode(packageMenu.getCategory());
            if (null == menuItem) {
                String msg = String.format("Cannot find system menu item by package menu's category: [%s]",
                        packageMenu.getCategory());
                log.error(msg);
                throw new WecubeCoreException(msg);
            }
            MenuItemDto packageMenuDto = MenuItemDto.fromPackageMenuItem(packageMenu, menuItem);

            result.add(packageMenuDto);
        });
        return result;
    }

}
