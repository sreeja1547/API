package com.ecommerce.productordermanagement.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommerce.productordermanagement.dto.AddressRequest;
import com.ecommerce.productordermanagement.dto.AddressResponse;
import com.ecommerce.productordermanagement.entity.Address;
import com.ecommerce.productordermanagement.entity.User;
import com.ecommerce.productordermanagement.exception.ResourceNotFoundException;
import com.ecommerce.productordermanagement.repository.AddressRepository;
import com.ecommerce.productordermanagement.repository.UserRepository;
import com.ecommerce.productordermanagement.service.AddressService;

@Service
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    public AddressServiceImpl(AddressRepository addressRepository,
                              UserRepository userRepository) {
        this.addressRepository = addressRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(User user) {
        return addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(user.getId())
                .stream().map(AddressResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponse getAddress(User user, Long addressId) {
        return AddressResponse.from(findOwned(user, addressId));
    }

    @Override
    @Transactional
    public AddressResponse createAddress(User user, AddressRequest request) {
        lockUser(user.getId());
        boolean makeDefault = request.isDefault()
                || addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(user.getId()).isEmpty();
        if (makeDefault) {
            clearDefaultForUser(user.getId());
        }
        Address address = new Address();
        address.setUser(userRepository.getReferenceById(user.getId()));
        copy(request, address);
        address.setDefault(makeDefault);
        return AddressResponse.from(addressRepository.save(address));
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(User user, Long addressId, AddressRequest request) {
        lockUser(user.getId());
        Address address = findOwned(user, addressId);
        if (request.isDefault()) {
            clearDefaultForUser(user.getId());
        }
        copy(request, address);
        address.setDefault(request.isDefault() || address.isDefault());
        return AddressResponse.from(addressRepository.save(address));
    }

    @Override
    @Transactional
    public void deleteAddress(User user, Long addressId) {
        lockUser(user.getId());
        Address address = findOwned(user, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(user.getId()).stream()
                    .findFirst().ifPresent(next -> {
                        next.setDefault(true);
                        addressRepository.save(next);
                    });
        }
    }

    @Override
    @Transactional
    public AddressResponse setDefault(User user, Long addressId) {
        lockUser(user.getId());
        Address address = findOwned(user, addressId);
        clearDefaultForUser(user.getId());
        address.setDefault(true);
        return AddressResponse.from(addressRepository.save(address));
    }

    private Address findOwned(User user, Long addressId) {
        return addressRepository.findByIdAndUserId(addressId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Address not found."));
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private void clearDefaultForUser(Long userId) {
        addressRepository.findAllByUserIdOrderByIsDefaultDescIdDesc(userId).stream()
            .filter(address -> address != null && address.isDefault())
                .forEach(address -> address.setDefault(false));
    }

    private void copy(AddressRequest request, Address address) {
        address.setFullName(request.fullName().trim());
        address.setPhone(request.phone().trim());
        address.setHouseNumber(request.houseNumber().trim());
        address.setStreet(request.street().trim());
        address.setArea(request.area().trim());
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPincode(request.pincode().trim());
        address.setLandmark(request.landmark() == null || request.landmark().isBlank()
                ? null : request.landmark().trim());
        String type = request.addressType().trim();
        address.setAddressType(type.equalsIgnoreCase("home") ? "Home"
            : type.equalsIgnoreCase("work") ? "Work" : "Other");
    }
}
