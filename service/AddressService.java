package com.ecommerce.productordermanagement.service;

import java.util.List;

import com.ecommerce.productordermanagement.dto.AddressRequest;
import com.ecommerce.productordermanagement.dto.AddressResponse;
import com.ecommerce.productordermanagement.entity.User;

public interface AddressService {

    List<AddressResponse> getAddresses(User user);

    AddressResponse getAddress(User user, Long addressId);

    AddressResponse createAddress(User user, AddressRequest request);

    AddressResponse updateAddress(User user, Long addressId, AddressRequest request);

    void deleteAddress(User user, Long addressId);

    AddressResponse setDefault(User user, Long addressId);
}
