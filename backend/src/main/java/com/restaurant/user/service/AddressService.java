package com.restaurant.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.restaurant.common.api.ErrorCode;
import com.restaurant.common.config.AppProperties;
import com.restaurant.common.enums.Role;
import com.restaurant.common.exception.BizException;
import com.restaurant.security.RoleGuard;
import com.restaurant.user.Address;
import com.restaurant.user.dto.AddressCreateRequest;
import com.restaurant.user.dto.AddressUpdateRequest;
import com.restaurant.user.mapper.AddressMapper;
import com.restaurant.user.vo.AddressView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 收货地址服务（docs/02-api.md §5）。
 *
 * <p>全部操作按当前用户隔离：越权访问返回 40302，地址不存在返回 40401；
 * 单用户地址数量受 {@code app.address.max-count} 限制（默认 20）。
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressMapper addressMapper;
    private final RoleGuard roleGuard;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public List<AddressView> list() {
        Long userId = requireCustomer();
        List<Address> addresses = addressMapper.selectList(Wrappers.<Address>lambdaQuery()
                .eq(Address::getUserId, userId)
                .orderByDesc(Address::getIsDefault)
                .orderByDesc(Address::getUpdatedAt)
                .orderByDesc(Address::getId));
        return addresses.stream().map(this::toView).toList();
    }

    @Transactional
    public AddressView create(AddressCreateRequest request) {
        Long userId = requireCustomer();
        Long count = addressMapper.selectCount(Wrappers.<Address>lambdaQuery().eq(Address::getUserId, userId));
        int maxCount = appProperties.getAddress().getMaxCount();
        if (count != null && count >= maxCount) {
            throw new BizException(ErrorCode.ADDRESS_LIMIT_EXCEEDED,
                    "地址数量已达上限（" + maxCount + " 条），请先删除不再使用的地址");
        }
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            clearDefault(userId, null);
        }
        Address address = new Address();
        address.setUserId(userId);
        applyFields(address, request.getConsignee(), request.getPhone(), request.getProvince(),
                request.getCity(), request.getDistrict(), request.getDetail());
        address.setIsDefault(makeDefault ? 1 : 0);
        addressMapper.insert(address);
        return toView(address);
    }

    @Transactional
    public AddressView update(Long id, AddressUpdateRequest request) {
        Long userId = requireCustomer();
        Address address = requireOwned(id, userId);
        boolean makeDefault = request.getIsDefault() != null
                ? request.getIsDefault()
                : Integer.valueOf(1).equals(address.getIsDefault());
        if (makeDefault) {
            clearDefault(userId, id);
        }
        applyFields(address, request.getConsignee(), request.getPhone(), request.getProvince(),
                request.getCity(), request.getDistrict(), request.getDetail());
        address.setIsDefault(makeDefault ? 1 : 0);
        int rows = addressMapper.updateById(address);
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_CONFLICT, "地址已变更，请刷新后重试");
        }
        return toView(addressMapper.selectById(id));
    }

    @Transactional
    public Map<String, Boolean> delete(Long id) {
        Long userId = requireCustomer();
        requireOwned(id, userId);
        addressMapper.deleteById(id);
        return Map.of("deleted", Boolean.TRUE);
    }

    @Transactional(readOnly = true)
    public AddressView get(Long id) {
        Long userId = requireCustomer();
        return toView(requireOwned(id, userId));
    }

    private Long requireCustomer() {
        return roleGuard.requireAnyRole(Role.CUSTOMER).getId();
    }

    private Address requireOwned(Long id, Long userId) {
        Address address = addressMapper.selectById(id);
        if (address == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "地址不存在");
        }
        if (!userId.equals(address.getUserId())) {
            throw new BizException(ErrorCode.RESOURCE_FORBIDDEN, "无权访问该地址");
        }
        return address;
    }

    private void clearDefault(Long userId, Long excludeId) {
        addressMapper.update(null, Wrappers.<Address>lambdaUpdate()
                .eq(Address::getUserId, userId)
                .eq(Address::getIsDefault, 1)
                .ne(excludeId != null, Address::getId, excludeId)
                .set(Address::getIsDefault, 0)
                .set(Address::getUpdatedAt, LocalDateTime.now()));
    }

    private void applyFields(Address address, String consignee, String phone, String province,
                             String city, String district, String detail) {
        address.setConsignee(consignee.trim());
        address.setPhone(phone.trim());
        address.setProvince(province.trim());
        address.setCity(city.trim());
        address.setDistrict(district.trim());
        address.setDetail(detail.trim());
    }

    private AddressView toView(Address address) {
        return new AddressView(address.getId(), address.getConsignee(), address.getPhone(),
                address.getProvince(), address.getCity(), address.getDistrict(), address.getDetail(),
                Integer.valueOf(1).equals(address.getIsDefault()),
                address.getCreatedAt(), address.getUpdatedAt());
    }
}
