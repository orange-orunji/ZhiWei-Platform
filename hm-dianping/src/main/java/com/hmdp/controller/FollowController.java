package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService iFollowService;

    /**
     *  是否关注
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable Long id){
        return iFollowService.isFollow(id);
    }

    /**
     * 关注或取关操作
     * @param id
     * @param isTrue
     * @return
     */
    @PutMapping("/{id}/{isTrue}")
    public Result follow(@PathVariable Long id,@PathVariable Boolean isTrue){
        return iFollowService.follow(id,isTrue);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result common(@PathVariable Long id){
        return iFollowService.common(id);
    }
}
