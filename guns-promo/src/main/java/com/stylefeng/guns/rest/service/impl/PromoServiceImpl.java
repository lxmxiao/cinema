package com.stylefeng.guns.rest.service.impl;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.dubbo.config.annotation.Service;
import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import com.stylefeng.guns.core.support.HttpKit;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoMapper;
import com.stylefeng.guns.rest.common.persistence.dao.MtimePromoStockMapper;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromo;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromoOrder;
import com.stylefeng.guns.rest.common.persistence.model.MtimePromoStock;
import com.stylefeng.guns.rest.dto.PromoDTO;
import com.stylefeng.guns.rest.mq.Producer;
import com.stylefeng.guns.rest.service.CinemaService;
import com.stylefeng.guns.rest.service.PromoService;
import com.stylefeng.guns.rest.vo.BaseResponVO;
import com.stylefeng.guns.rest.vo.cinema.CinemaInfoVO;
import com.stylefeng.guns.rest.vo.promo.PromoOrderVO;
import com.stylefeng.guns.rest.vo.promo.PromoVO;
import com.stylefeng.guns.rest.vo.user.UserVO;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@Service(interfaceClass = PromoService.class)
public class PromoServiceImpl implements PromoService {

    @Autowired
    private Producer producer;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MtimePromoStockMapper promoStockMapper;

    @Autowired
    private MtimePromoMapper promoMapper;

    @Reference(interfaceClass = CinemaService.class, check = false)
    private CinemaService cinemaService;

    /**
     * 获取秒杀列表
     * @param promoDTO
     * @return
     */
    @Override
    public BaseResponVO getPromo(PromoDTO promoDTO) {
        Page<MtimePromo> mtimePromoPage = new Page<>(promoDTO.getNowPage(), promoDTO.getPageSize());
        List<MtimePromo> mtimePromos = promoMapper.selectPage(mtimePromoPage, null);
        List<PromoVO> list = new ArrayList<>();
        if (mtimePromos == null){
            return new BaseResponVO(0,null,list,null,null,null);
        }

        //获取所需要显示的的数据
        for (MtimePromo mtimePromo : mtimePromos) {
            PromoVO promoVO = new PromoVO();
            CinemaInfoVO cinemaInfoVO = cinemaService.getCinemaInfoVOByCinemaId(mtimePromo.getCinemaId());
            promoVO.setCinemaId(cinemaInfoVO.getCinemaId());
            promoVO.setCinemaName(cinemaInfoVO.getCinemaName());
            promoVO.setCinemaAddress(cinemaInfoVO.getCinemaAdress());
            promoVO.setImgAddress(cinemaInfoVO.getImgUrl());
            promoVO.setDescription(mtimePromo.getDescription());
            promoVO.setEndTime(mtimePromo.getEndTime());
            promoVO.setStartTime(mtimePromo.getStartTime());
            promoVO.setPrice((int) mtimePromo.getPrice().doubleValue());
            promoVO.setStatus(mtimePromo.getStatus());
            promoVO.setUuid(mtimePromo.getUuid());

            //获取秒杀库存
//            EntityWrapper<MtimePromoStock> mtimePromoStockEntityWrapper = new EntityWrapper<>();
//            mtimePromoStockEntityWrapper.eq("promo_id",mtimePromo.getUuid());
//            MtimePromoStock mtimePromoStock = promoStockMapper.selectList(mtimePromoStockEntityWrapper).get(0);
//            promoVO.setStock(mtimePromoStock.getStock());
            List<MtimePromoStock> promo_stock = (List<MtimePromoStock>) redisTemplate.opsForValue().get("promo_stock");
            for (MtimePromoStock mtimePromoStock : promo_stock) {
                if (mtimePromo.getUuid().equals(mtimePromoStock.getPromoId())){
                    promoVO.setStock(mtimePromoStock.getStock());
                }
            }
            list.add(promoVO);
        }
        return new BaseResponVO(0,null,list,null,null,null);
    }

    /**
     * 秒杀下单接口
     * @param promoId
     * @param amount
     * @return
     */
    @Override
    public BaseResponVO createPromoOrder(Integer promoId, Integer amount, String promoToken) throws Exception {

        //获得所登录的用户信息
        String token = HttpKit.getRequest().getHeader("Authorization").substring(7);
        UserVO user = (UserVO) redisTemplate.opsForValue().get(token);
        List<MtimePromoStock> promoStockList = (List<MtimePromoStock>) redisTemplate.opsForValue().get("promo_stock");

        if (user == null){
            return new BaseResponVO(1,null,null,null,null,null);
        }

        MtimePromo mtimePromo = promoMapper.selectById(promoId);
        MtimePromoOrder mtimePromoOrder = new MtimePromoOrder();
        mtimePromoOrder.setUuid(user.getUuid());
        mtimePromoOrder.setCinemaId(mtimePromo.getCinemaId());
        //兑换码还没有

        mtimePromoOrder.setAmount(amount);
        mtimePromoOrder.setPrice(mtimePromo.getPrice());
        //兑换开始时间还没有

        mtimePromoOrder.setCreateTime(new Date());
        //兑换结束时间还没有

        //创建消息队列修改mysql数据库库存
        producer.decreaseStock(promoId,amount);

        //更改redis中的库存
        for (MtimePromoStock mtimePromoStock : promoStockList) {
            if (promoId.equals(mtimePromoStock.getPromoId())){
                mtimePromoStock.setStock(mtimePromoStock.getStock()-amount);
            }
        }
        redisTemplate.opsForValue().set("promo_stock",promoStockList);

        return new BaseResponVO(0,"发布成功");
    }

    /**
     * 将库存信息发布到缓存
     * @return
     */
    @Override
    public BaseResponVO publishPromoStock() {
        List<MtimePromoStock> promoStock = (List<MtimePromoStock>) redisTemplate.opsForValue().get("promo_stock");
        if (promoStock == null){
            List<MtimePromoStock> mtimePromos = promoStockMapper.selectList(null);
            redisTemplate.opsForValue().set("promo_stock",mtimePromos);
            redisTemplate.expire("promo_stock",365 * 100, TimeUnit.DAYS);
        }
        return new BaseResponVO(0,"发布成功");
    }
}
