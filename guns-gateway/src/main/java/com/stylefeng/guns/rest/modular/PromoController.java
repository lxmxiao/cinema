package com.stylefeng.guns.rest.modular;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.RateLimiter;
import com.stylefeng.guns.core.exception.GunsException;
import com.stylefeng.guns.core.exception.GunsExceptionEnum;
import com.stylefeng.guns.core.support.HttpKit;
import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.service.PromoService;
import com.stylefeng.guns.rest.vo.BaseResponVO;
import com.stylefeng.guns.rest.vo.user.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("promo")
@Slf4j
public class PromoController {

    @Reference(interfaceClass = PromoService.class, check = false)
    private PromoService promoService;

    @Autowired
    private RedisTemplate redisTemplate;

    //设置一个线程池
    private ExecutorService executorService ;

    //声明一个令牌桶
    private RateLimiter rateLimiter;

    @PostConstruct
    public void init() {
        //初始化一个固定线程大小的线程池
        executorService = Executors.newFixedThreadPool(100);

//        //初始化一个动态扩容无线程限制的线程池
//        executorService = Executors.newCachedThreadPool();
//
//        //创建一个只有一个线程的线程池
//        executorService = Executors.newSingleThreadExecutor();
//
//        //创建一个定时任务去执行的线程池
//        executorService = Executors.newScheduledThreadPool(10);

        //每秒产生10个令牌
        rateLimiter = RateLimiter.create(10);
    }

    @RequestMapping("getPromo")
    public BaseResponVO getPromo(PromoDTO promoDTO){
        BaseResponVO baseResponVO = promoService.getPromo(promoDTO);
        return baseResponVO;
    }

    @RequestMapping("createOrder")
    public BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken, HttpServletRequest request, HttpServletResponse response) throws Exception {

        //限流 返回的是等待时间 其實就是去獲取一個令牌 返回的結果就是綫程等待的時間
        /*double acquire = rateLimiter.acquire();

        if (acquire < 0) {
            log.info("秒杀失败  " + new Date());
            return new BaseResponVO(1,"秒杀失败");
        }*/

        String token = request.getHeader("Authorization").substring(7);
        UserVO user = (UserVO) redisTemplate.opsForValue().get(token);

        //判断秒杀令牌是否合法
        /*Boolean res = redisTemplate.hasKey("user_promo_token_" + promoId + "_userId_" + user.getUuid());
        if (!res) {
            log.info("秒杀令牌不合法！  " + new Date());
            return new BaseResponVO(1,"秒杀令牌不合法！");
        }
        String tokenInRedis = (String) redisTemplate.opsForValue().get("user_promo_token_" + promoId + "_userId_" + user.getUuid());
        if (!tokenInRedis.equals(promoToken)) {
            log.info("秒杀令牌不合法！  " + new Date());
            return new BaseResponVO(1,"秒杀令牌不合法！");
        }*/

        // 初始化秒杀状态表
        String stockLogId = promoService.initPromoStockLog(promoId,amount);
        if (StringUtils.isBlank(stockLogId)){
            log.info("下单失败！ promoId:{},userId:{},amount:{}",promoId,user.getUuid(),amount);
            return new BaseResponVO(1,"下单失败");
        }

        // 创建订单
        Boolean result = promoService.savePromoOrderInTransaction(promoId,amount,user.getUuid(),stockLogId);

        if (!result) {
            log.info("下单失败");
            return new BaseResponVO(1,"下单失败");
        }
        return new BaseResponVO(0,"下单成功");

//        String token = HttpKit.getRequest().getHeader("Authorization").substring(7);
//        Object object =  redisTemplate.opsForValue().get(token);
//        UserVO userVO = (UserVO) object;
//        BaseResponVO baseResponVO = promoService.createPromoOrder(promoId,amount,promoToken,userVO);
//        return baseResponVO;
    }

    @RequestMapping("publishPromoStock")
    public BaseResponVO publishPromoStock(){
        BaseResponVO baseResponVO = promoService.publishPromoStock();
        return baseResponVO;
    }

    @RequestMapping("generateToken")
    public BaseResponVO generateToken(Integer promoId){

        /*String token = HttpKit.getRequest().getHeader("Authorization").substring(7);
        UserVO user = (UserVO) redisTemplate.opsForValue().get(token);

        Boolean result = redisTemplate.hasKey("promo_stock" + promoId);

        if (!result) {
            log.info("库存售罄  " + new Date());
            return new BaseResponVO(1,"库存售罄");
        }

        if (user == null) {
            log.info("获取用户失败！请用户重新登录！user:{}", JSON.toJSONString(user));
            return new BaseResponVO(1,"未登录");
        }

        String promoToken = promoService.generateToken(promoId,user);
        if (StringUtils.isBlank(promoToken)) {
            return new BaseResponVO(1,"获取秒杀令牌失败");
        }
        return new BaseResponVO(0,promoToken);*/
        BaseResponVO baseResponVO = new BaseResponVO();
        baseResponVO.setStatus(0);
        return baseResponVO;
    }
}
