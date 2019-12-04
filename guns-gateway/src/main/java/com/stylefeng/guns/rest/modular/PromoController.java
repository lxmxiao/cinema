package com.stylefeng.guns.rest.modular;

import com.alibaba.dubbo.config.annotation.Reference;
import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.service.PromoService;
import com.stylefeng.guns.rest.vo.BaseResponVO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("promo")
public class PromoController {

    @Reference(interfaceClass = PromoService.class, check = false)
    private PromoService promoService;

    @RequestMapping("getPromo")
    public BaseResponVO getPromo(PromoDTO promoDTO){
        BaseResponVO baseResponVO = promoService.getPromo(promoDTO);
        return baseResponVO;
    }

    @RequestMapping("createOrder")
    public BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken) throws Exception {
        BaseResponVO baseResponVO = promoService.createPromoOrder(promoId,amount,promoToken);
        return baseResponVO;
    }

    @RequestMapping("publishPromoStock")
    public BaseResponVO publishPromoStock(){
        BaseResponVO baseResponVO = promoService.publishPromoStock();
        return baseResponVO;
    }
}
