package com.stylefeng.guns.rest.vo.promo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class PromoOrderVO implements Serializable {

    private String uuid;
    private Integer userId;
    private Integer cinemaId;
    private String exchangeCode;
    private Integer amount;
    private Integer price;
    private Date startTime;
    private Date createTime;
    private Date endTime;
}
