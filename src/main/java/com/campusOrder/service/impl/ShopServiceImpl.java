package com.campusOrder.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campusOrder.dto.Result;
import com.campusOrder.entity.Shop;
import com.campusOrder.mapper.ShopMapper;
import com.campusOrder.service.IShopService;
import com.campusOrder.utils.CacheClient;
import com.campusOrder.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.campusOrder.utils.RedisConstants.*;

/**
 * <p>
 *  鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 瑙ｅ喅缂撳瓨绌块€?
        Shop shop = cacheClient
                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 浜掓枼閿佽В鍐崇紦瀛樺嚮绌?
        // Shop shop = cacheClient
        //         .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 閫昏緫杩囨湡瑙ｅ喅缂撳瓨鍑荤┛
        // Shop shop = cacheClient
        //         .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);

        if (shop == null) {
            return Result.fail("搴楅摵涓嶅瓨鍦紒");
        }
        // 7.杩斿洖
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("搴楅摵id涓嶈兘涓虹┖");
        }
        // 1.鏇存柊鏁版嵁搴?
        updateById(shop);
        // 2.鍒犻櫎缂撳瓨
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.鍒ゆ柇鏄惁闇€瑕佹牴鎹潗鏍囨煡璇?
        if (x == null || y == null) {
            // 涓嶉渶瑕佸潗鏍囨煡璇紝鎸夋暟鎹簱鏌ヨ
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 杩斿洖鏁版嵁
            return Result.ok(page.getRecords());
        }

        // 2.璁＄畻鍒嗛〉鍙傛暟
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.鏌ヨredis銆佹寜鐓ц窛绂绘帓搴忋€佸垎椤点€傜粨鏋滐細shopId銆乨istance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.瑙ｆ瀽鍑篿d
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 娌℃湁涓嬩竴椤典簡锛岀粨鏉?
            return Result.ok(Collections.emptyList());
        }
        // 4.1.鎴彇 from ~ end鐨勯儴鍒?
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.鑾峰彇搴楅摵id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.鑾峰彇璺濈
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.鏍规嵁id鏌ヨShop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.杩斿洖
        return Result.ok(shops);
    }
}

