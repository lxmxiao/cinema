package com.stylefeng.guns.rest.service;

import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.vo.BaseResponVO;
import com.stylefeng.guns.rest.vo.promo.PromoOrderVO;
import com.stylefeng.guns.rest.vo.user.UserVO;

public interface PromoService {

    BaseResponVO getPromo(PromoDTO promoDTO);

//    BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken, UserVO user) throws Exception;

    BaseResponVO publishPromoStock();

    String initPromoStockLog(Integer promoId, Integer amount);

    Boolean savePromoOrderInTransaction(Integer promoId, Integer amount, Integer userId, String stockLogId);

    PromoOrderVO savePromoOrderVO(Integer promoId, Integer amount, Integer userId, String stockLogId);

    BaseResponVO generateToken(Integer promoId);
}
