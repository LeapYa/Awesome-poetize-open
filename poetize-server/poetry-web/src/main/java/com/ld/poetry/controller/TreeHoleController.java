package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.aop.AuditLog;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.aop.SaveCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.TreeHoleMapper;
import com.ld.poetry.entity.TreeHole;
import com.ld.poetry.enums.CodeMsg;
import com.ld.poetry.service.CaptchaService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.XssFilterUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * <p>
 * 弹幕 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-09-14
 */
@RestController
@RequestMapping("/webInfo")
@Slf4j
public class TreeHoleController {

    @Autowired
    private TreeHoleMapper treeHoleMapper;

    @Autowired
    private CaptchaService captchaService;

    /**
     * 保存
     */
    @PostMapping("/saveTreeHole")
    @SaveCheck
    public PoetryResult<TreeHole> saveTreeHole(@RequestBody TreeHole treeHole) {
        if (!StringUtils.hasText(treeHole.getMessage())) {
            return PoetryResult.fail("留言不能为空！");
        }
        
        // 检查是否需要验证码（树洞使用comment配置）
        boolean captchaRequired = captchaService.isCaptchaRequired("comment");
        String verificationToken = treeHole.getVerificationToken();
        
        if (captchaRequired) {
            // 验证码开启时，必须提供有效token
            if (!StringUtils.hasText(verificationToken)) {
                log.warn("树洞留言需要验证码但未提供token，拒绝请求");
                return PoetryResult.fail(CodeMsg.CAPTCHA_REQUIRED.getCode(), "请先完成验证码验证");
            }
            
            boolean isTokenValid = captchaService.verifyToken("comment", verificationToken, null, null);
            if (!isTokenValid) {
                log.warn("树洞留言验证码token验证失败，拒绝提交");
                return PoetryResult.fail(CodeMsg.CAPTCHA_INVALID.getCode(), "验证码验证失败，请重新验证后再试");
            }

            log.info("树洞留言验证码token验证通过");
        }
        
        // XSS过滤处理
        String cleanMessage = XssFilterUtil.clean(treeHole.getMessage());
        if (!StringUtils.hasText(cleanMessage)) {
            return PoetryResult.fail("留言内容不合法！");
        }
        treeHole.setMessage(cleanMessage);
        
        treeHoleMapper.insert(treeHole);
        if (!StringUtils.hasText(treeHole.getAvatar())) {
            treeHole.setAvatar(PoetryUtil.getRandomAvatar(null));
        }
        return PoetryResult.success(treeHole);
    }


    /**
     * 删除
     */
    @GetMapping("/deleteTreeHole")
    @LoginCheck(0)
    @AuditLog(action = "TREE_HOLE_DELETE", targetType = "TREE_HOLE", targetIdParam = "id", summary = "删除留言")
    public PoetryResult deleteTreeHole(@RequestParam("id") Integer id) {
        treeHoleMapper.deleteById(id);
        return PoetryResult.success();
    }


    /**
     * 查询List
     */
    @GetMapping("/listTreeHole")
    public PoetryResult<List<TreeHole>> listTreeHole() {
        List<TreeHole> treeHoles;
        Long count = new LambdaQueryChainWrapper<>(treeHoleMapper).count();
        if (count > CommonConst.TREE_HOLE_COUNT) {
            int i = new Random().nextInt(count.intValue() + 1 - CommonConst.TREE_HOLE_COUNT);
            treeHoles = treeHoleMapper.queryAllByLimit(i, CommonConst.TREE_HOLE_COUNT);
        } else {
            treeHoles = new LambdaQueryChainWrapper<>(treeHoleMapper).list();
        }

        treeHoles.forEach(treeHole -> {
            if (!StringUtils.hasText(treeHole.getAvatar())) {
                treeHole.setAvatar(PoetryUtil.getRandomAvatar(treeHole.getId().toString()));
            }
        });
        return PoetryResult.success(treeHoles);
    }
}
