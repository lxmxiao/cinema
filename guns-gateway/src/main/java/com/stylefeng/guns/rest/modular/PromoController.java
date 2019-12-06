package com.stylefeng.guns.rest.modular;

import com.alibaba.dubbo.config.annotation.Reference;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("promo")
@Slf4j
public class PromoController {

    @Reference(interfaceClass = PromoService.class, check = false)
    private PromoService promoService;

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping("getPromo")
    public BaseResponVO getPromo(PromoDTO promoDTO){
        BaseResponVO baseResponVO = promoService.getPromo(promoDTO);
        return baseResponVO;
    }

    @RequestMapping("createOrder")
    public BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String token = request.getHeader("Authorization").substring(7);
        UserVO user = (UserVO) redisTemplate.opsForValue().get(token);

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
        BaseResponVO baseResponVO = promoService.generateToken(promoId);
        return baseResponVO;
    }
}
