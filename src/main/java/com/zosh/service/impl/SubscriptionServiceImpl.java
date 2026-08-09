package com.zosh.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.zosh.domain.PaymentStatus;
import com.zosh.domain.PaymentType;
import com.zosh.exception.PaymentException;
import com.zosh.exception.SubscriptionException;
import com.zosh.exception.UserException;
import com.zosh.mapper.SubscriptionMapper;
import com.zosh.modal.Payment;
import com.zosh.modal.Subscription;
import com.zosh.modal.SubscriptionPlan;
import com.zosh.modal.User;
import com.zosh.payload.dto.SubscriptionDTO;
import com.zosh.payload.request.PaymentInitiateRequest;
import com.zosh.payload.request.SubscribeRequest;
import com.zosh.payload.response.PaymentInitiateResponse;
import com.zosh.repository.PaymentRepository;
import com.zosh.repository.SubscriptionRepository;
import com.zosh.repository.UserRepository;
import com.zosh.service.PaymentService;
import com.zosh.service.SubscriptionService;
import com.zosh.service.UserService;
import com.zosh.service.gateway.RazorpayService;
import com.zosh.service.gateway.StripeService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of SubscriptionService
 * Handles subscription creation, renewal, cancellation with payment integration
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionMapper subscriptionMapper;
    private final RazorpayService razorpayService;
    private final StripeService stripeService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final com.zosh.repository.SubscriptionPlanRepository subscriptionPlanRepository;

    @Override
    @Transactional
    public PaymentInitiateResponse subscribe(SubscribeRequest request)
            throws SubscriptionException, UserException, PaymentException {

        log.info("Processing subscription request for user: {}, plan ID: {}",
            request.getUserId(), request.getPlanId());

        // 1. Get current user
        User user = getCurrentAuthenticatedUser();


        // 2. Get subscription plan
        SubscriptionPlan plan = subscriptionPlanRepository
                .findById(request.getPlanId())
            .orElseThrow(() -> new SubscriptionException("Subscription plan not found with ID: " + request.getPlanId()));

        // Validate plan is active
        if (!plan.getIsActive()) {
            throw new SubscriptionException("Subscription plan is not currently available: " + plan.getName());
        }

        // 3. Check if user already has an active subscription
        Optional<Subscription> existingSubscription = subscriptionRepository
            .findActiveSubscriptionByUserId(user.getId(), LocalDate.now());

        if (existingSubscription.isPresent()) {
            throw new SubscriptionException(
                "User already has an active subscription. Please cancel it before subscribing to a new plan.");
        }

        // 4. Create new subscription entity
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(plan);
        subscription.setAutoRenew(request.getAutoRenew() != null ? request.getAutoRenew() : false);

        // Initialize from plan (sets price, maxBooks, maxDays, dates)
        subscription.initializeFromPlan();

        // Subscription starts as inactive until payment is confirmed
        subscription.setIsActive(false);

        subscription = subscriptionRepository.save(subscription);
        log.info("Subscription created with ID: {}", subscription.getId());

        // 5. Create payment entity
        PaymentInitiateRequest paymentInitiateRequest = PaymentInitiateRequest
                .builder()
                .userId(user.getId())
                .subscriptionId(subscription.getId())
                .paymentType(PaymentType.MEMBERSHIP)
                .gateway(request.getPaymentGateway())
                .amount(subscription.getPrice())
                .currency(subscription.getCurrency())
                .description("Library Subscription - " + plan.getName())
                .build();

        return paymentService
                .initiatePayment(paymentInitiateRequest);
    }

    @Override
    public SubscriptionDTO activateSubscription(Long subscriptionId, Long paymentId)
            throws SubscriptionException {

        log.info("Activating subscription: {} after payment: {}", subscriptionId, paymentId);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionException("Subscription not found with ID: " + subscriptionId));

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new SubscriptionException("Payment not found with ID: " + paymentId));

        // Verify payment is successful
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new SubscriptionException(
                "Cannot activate subscription. Payment status is: " + payment.getStatus());
        }

        // Verify payment belongs to this subscription
        if (!payment.getSubscription().getId().equals(subscriptionId)) {
            throw new SubscriptionException("Payment does not belong to this subscription");
        }

        // Activate subscription
        subscription.setIsActive(true);

        // Ensure start date is set
        if (subscription.getStartDate() == null 
        || subscription.getStartDate().isBefore(LocalDate.now())) {
            subscription.setStartDate(LocalDate.now());
            subscription.calculateEndDate();
        }

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription activated successfully: {}", subscriptionId);
        return subscriptionMapper.toDTO(subscription);
    }

    @Override
    public SubscriptionDTO getUsersActiveSubscription(Long userId)
            throws SubscriptionException, UserException {

        if(userId!=null){
            if (!userRepository.existsById(userId)) {
                throw new SubscriptionException("User not found with ID: " + userId);
            }
        }
        else{
            User user=userService.getCurrentUser();
            userId=user.getId();
        }

        Subscription subscription = subscriptionRepository
            .findActiveSubscriptionByUserId(userId, LocalDate.now())
            .orElseThrow(() -> new SubscriptionException(
                "No active subscription found for user ID: "));
        return subscriptionMapper.toDTO(subscription);
    }

    @Override
    public List<SubscriptionDTO> getUserSubscriptions(Long userId)
            throws SubscriptionException, UserException {



        if(userId!=null){
            if (!userRepository.existsById(userId)) {
                throw new SubscriptionException("User not found with ID: " + userId);
            }
        }else{
            User user=userService.getCurrentUser();
            userId=user.getId();
        }


        List<Subscription> subscriptions = subscriptionRepository
            .findByUserIdOrderByCreatedAtDesc(userId);

        return subscriptions.stream().map(subscriptionMapper::toDTO).toList();
    }

    @Override
    public PaymentInitiateResponse renewSubscription(Long subscriptionId, SubscribeRequest request)
            throws SubscriptionException, UserException, PaymentException {

        log.info("Renewing subscription: {}", subscriptionId);

        Subscription oldSubscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionException(
                "Subscription not found with ID: " + subscriptionId));

        // Cancel old subscription if still active
        if (oldSubscription.getIsActive()) {
            oldSubscription.setIsActive(false);
            oldSubscription.setCancelledAt(LocalDateTime.now());
            oldSubscription.setCancellationReason("Renewed to new subscription");
            subscriptionRepository.save(oldSubscription);
        }

        // Create new subscription with same or different plan
        request.setUserId(oldSubscription.getUser().getId());
        if (request.getPlanId() == null) {
            request.setPlanId(oldSubscription.getPlan().getId());
        }

        return subscribe(request);
    }

    @Override
    public SubscriptionDTO cancelSubscription(Long subscriptionId, String reason)
            throws SubscriptionException {

        log.info("Cancelling subscription: {} with reason: {}", subscriptionId, reason);

        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new SubscriptionException(
                "Subscription not found with ID: " + subscriptionId));

        if (!subscription.getIsActive()) {
            throw new SubscriptionException("Subscription is already inactive");
        }

        // Mark as cancelled
        subscription.setIsActive(false);
        subscription.setCancelledAt(LocalDateTime.now());
        subscription.setCancellationReason(reason != null ? reason : "Cancelled by user");

        subscription = subscriptionRepository.save(subscription);

        log.info("Subscription cancelled successfully: {}", subscriptionId);
        return subscriptionMapper.toDTO(subscription);
    }

    @Override
    public SubscriptionDTO getSubscriptionById(Long id) throws SubscriptionException {
        Subscription subscription = subscriptionRepository.findById(id)
            .orElseThrow(() -> new SubscriptionException("Subscription not found with ID: " + id));

        return subscriptionMapper.toDTO(subscription);
    }

    @Override
    public List<SubscriptionDTO> getAllActiveSubscriptions(Pageable pageable) {
        List<Subscription> subscriptions = subscriptionRepository
            .findAll();

        return subscriptions.stream().map(
                subscriptionMapper::toDTO
        ).toList();
    }

    @Override
    public void deactivateExpiredSubscriptions() {
        log.info("Running subscription expiry check at {}", LocalDateTime.now());

        List<Subscription> expiredSubscriptions = subscriptionRepository
            .findExpiredActiveSubscriptions(LocalDate.now());

        int deactivatedCount = 0;
        for (Subscription subscription : expiredSubscriptions) {
            subscription.setIsActive(false);
            subscription.setNotes(
                (subscription.getNotes() != null ? subscription.getNotes() + "\n" : "") +
                "Auto-deactivated on " + LocalDate.now() + " due to expiry");
            subscriptionRepository.save(subscription);
            deactivatedCount++;

            log.debug("Deactivated expired subscription ID: {} for user: {}",
                subscription.getId(), subscription.getUser().getEmail());
        }

        log.info("Deactivated {} expired subscriptions", deactivatedCount);
    }

    @Override
    public boolean hasValidSubscription(Long userId) {
        return subscriptionRepository
        .hasActiveSubscription(userId, LocalDate.now());
    }

    // ==================== EVENT LISTENERS ====================

    /** 
     * Event listener for payment success events.
     * Automatically activates subscription when a subscription payment succeeds.
     * This decouples payment processing from subscription activation.
     *
     * @param event Payment success event containing payment details
     */
//    @EventListener
//    @Async
//    @Transactional
//    public void handlePaymentSuccess(PaymentSuccessEvent event) {
//        log.info("Received PaymentSuccessEvent for payment ID: {}, type: {}",
//            event.getPaymentId(), event.getPaymentType());
//
//        // Only process subscription payments
//        if (event.getPaymentType() == PaymentType.MEMBERSHIP && event.getSubscriptionId() != null) {
//            try {
//                log.info("Activating subscription {} after payment success",
//                    event.getSubscriptionId());
//
//                activateSubscription(
//                        event.getSubscriptionId(),
//                        event.getPaymentId()
//                );
//
//                log.info("Subscription {} activated successfully via event",
//                    event.getSubscriptionId());
//
//            } catch (SubscriptionException e) {
//                log.error("Failed to activate subscription {} via event: {}",
//                    event.getSubscriptionId(), e.getMessage(), e);
//                // Event handling failure - could trigger compensation logic here
//            }
//        }
//    }

    // ==================== HELPER METHODS ====================

    /**
     * Get currently authenticated user
     */
    private User getCurrentAuthenticatedUser() throws UserException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UserException("User not authenticated");
        }

        String email = authentication.getName();
        User user = userRepository.findByEmail(email);

        if (user == null) {
            throw new UserException("Authenticated user not found");
        }

        return user;
    }
}
