package com.restaurant.user.adapter;

import com.restaurant.contract.AddressPort;
import com.restaurant.user.Address;
import com.restaurant.user.mapper.AddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** {@link AddressPort} 的用户域实现。 */
@Component
@RequiredArgsConstructor
public class AddressPortAdapter implements AddressPort {

    private final AddressMapper addressMapper;

    @Override
    public AddressSnapshot getOwned(Long addressId, Long userId) {
        if (addressId == null || userId == null) {
            return null;
        }
        Address address = addressMapper.selectById(addressId);
        if (address == null || !userId.equals(address.getUserId())) {
            return null;
        }
        return new AddressSnapshot(address.getId(), address.getUserId(), address.getConsignee(),
                address.getPhone(), address.getProvince(), address.getCity(), address.getDistrict(),
                address.getDetail());
    }
}
