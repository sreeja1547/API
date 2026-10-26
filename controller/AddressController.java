package com.ecommerce.productordermanagement.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ecommerce.productordermanagement.dto.AddressRequest;
import com.ecommerce.productordermanagement.dto.AddressResponse;
import com.ecommerce.productordermanagement.service.AddressService;
import com.ecommerce.productordermanagement.service.SessionUserService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/addresses")
@Validated
public class AddressController {

    private final AddressService addressService;
    private final SessionUserService sessionUserService;

    public AddressController(AddressService addressService,
                             SessionUserService sessionUserService) {
        this.addressService = addressService;
        this.sessionUserService = sessionUserService;
    }

    @GetMapping
    public List<AddressResponse> getAddresses(HttpSession session) {
        return addressService.getAddresses(sessionUserService.requireUser(session));
    }

    @GetMapping("/{id}")
    public AddressResponse getAddress(@PathVariable Long id, HttpSession session) {
        return addressService.getAddress(sessionUserService.requireUser(session), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse createAddress(@Valid @RequestBody AddressRequest request,
                                         HttpSession session) {
        return addressService.createAddress(sessionUserService.requireUser(session), request);
    }

    @PutMapping("/{id}")
    public AddressResponse updateAddress(@PathVariable Long id,
                                         @Valid @RequestBody AddressRequest request,
                                         HttpSession session) {
        return addressService.updateAddress(sessionUserService.requireUser(session), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(@PathVariable Long id, HttpSession session) {
        addressService.deleteAddress(sessionUserService.requireUser(session), id);
    }

    @PutMapping("/{id}/default")
    public AddressResponse setDefault(@PathVariable Long id, HttpSession session) {
        return addressService.setDefault(sessionUserService.requireUser(session), id);
    }
}
