package com.one.service;

import com.one.exception.PaymentException;
import com.one.payload.dto.PaymentDTO;
import com.one.payload.request.PaymentInitiateRequest;
import com.one.payload.request.PaymentVerifyRequest;
import com.one.payload.response.PaymentInitiateResponse;
import com.one.payload.response.RevenueStatisticsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for payment operations
 * Handles payment initiation, verification, and management
 */
public interface PaymentService {

    /**
     * Initiate a new payment (creates order with payment gateway)
     */
    PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) throws PaymentException;

    /**
     * Verify payment after gateway callback
     */
    PaymentDTO verifyPayment(PaymentVerifyRequest request) throws PaymentException;

    /**
     * Get payment by ID
     */
    PaymentDTO getPaymentById(Long paymentId) throws PaymentException;

    /**
     * Get payment by transaction ID
     */
    PaymentDTO getPaymentByTransactionId(String transactionId) throws PaymentException;

    /**
     * Get all payments for a user
     */
    Page<PaymentDTO> getUserPayments(Long userId, Pageable pageable) throws PaymentException;

    /**
     * Get all payments (admin)
     */
    Page<PaymentDTO> getAllPayments(Pageable pageable);

    /**
     * Cancel a pending payment
     */
    PaymentDTO cancelPayment(Long paymentId) throws PaymentException;

    /**
     * Retry a failed payment
     */
    PaymentInitiateResponse retryPayment(Long paymentId) throws PaymentException;

    /**
     * Get monthly revenue statistics (Admin only)
     */
    RevenueStatisticsResponse getMonthlyRevenue();
}
