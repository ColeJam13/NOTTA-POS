import { useState, useEffect } from 'react';
import PaymentForm from './PaymentForm';
import './PaymentModal.css';
import { apiFetch, getJson, ORDER_NOT_OPEN } from '../../services/api';
import { formatTaxRate } from '../../utils/totals';

/**
 * The pop-up for closing a check: shows what is owed, takes a tip and a
 * payment method, and records the payment.
 *
 * The amounts come from the backend (GET /api/orders/{id}/check), not from the
 * items on the order screen. The backend adds up the items that were actually
 * saved and sent, so the number shown here is the number that gets charged.
 * (This used to add up whatever the screen was holding, including items that
 * were never saved.)
 *
 * onCheckClosedElsewhere (optional) is called instead of showing an alert when
 * the backend says the check was already paid, which happens when two terminals
 * have the same check up and the other one pressed first. The screen that opened
 * this window knows how to catch up with the table; this window does not.
 */
function PaymentModal({ order, onClose, onPaymentSuccess, onCheckClosedElsewhere }) {
  const [paymentMethod, setPaymentMethod] = useState('credit_card');
  const [tipAmount, setTipAmount] = useState('');
  const [tipPercent, setTipPercent] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);
  const [paymentSuccess, setPaymentSuccess] = useState(false);

  // The check from the backend: { subtotal, tax, total, taxRate, itemCount, unsentItemCount }.
  // null until it has loaded.
  const [check, setCheck] = useState(null);
  const [checkError, setCheckError] = useState('');

  // Load the check when the modal opens
  useEffect(() => {
    getJson(`/api/orders/${order.orderId}/check`)
      .then(data => setCheck(data))
      .catch(error => {
        console.error('Error loading check:', error);
        setCheckError('Could not load the check for this order. Close this window and try again.');
      });
  }, [order.orderId]);

  // Calculate order total
  // (zeros until the check has loaded - the form is not shown before then)
  const subtotal = check ? check.subtotal : 0;
  const tax = check ? check.tax : 0;
  const total = check ? check.total : 0;
  const taxRateLabel = check ? formatTaxRate(check.taxRate) : '';                 // 0.03 -> "3%" (same label as the order screen)
  const tipValue = parseFloat(tipAmount) || 0;
  const grandTotal = total + tipValue;

  const handleTipPercentChange = (percent) => {
    setTipPercent(percent);
    setTipAmount((total * (percent / 100)).toFixed(2));
  };

  const handleTipAmountChange = (amount) => {
    setTipAmount(amount);
    setTipPercent('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setIsProcessing(true);

    try {
      const response = await apiFetch('/api/payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          orderId: order.orderId,
          amount: total,
          tipAmount: tipValue,
          paymentMethod: paymentMethod
        })
      });

      if (response.ok) {
        setPaymentSuccess(true);
        
        // Wait 2 seconds to show success message, then call success callback
        setTimeout(() => {
          onPaymentSuccess();
        }, 2000);
      } else {
        // The backend says why in "message" (unsent items, check already closed,
        // the order changed since this window opened, ...). Show that to the server.
        const error = await response.json().catch(() => null);

        // Paid on another terminal a moment ago: hand over to the screen behind this window
        if (error?.code === ORDER_NOT_OPEN && onCheckClosedElsewhere) {
          onCheckClosedElsewhere();
          return;
        }

        alert(error?.message || 'Payment failed. Please try again.');
        setIsProcessing(false);
      }
    } catch (error) {
      console.error('Payment error:', error);
      alert('Payment failed. Please try again.');
      setIsProcessing(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h2>CLOSE ORDER</h2>
          <button className="modal-close" onClick={onClose}>✕</button>
        </div>

        <div className="modal-body">
          {paymentSuccess ? (
            <div className="payment-success-message">
              <div className="success-icon">✓</div>
              <h3>Payment Processed Successfully!</h3>
              <p>Returning to order screen...</p>
            </div>
          ) : checkError ? (
            <p className="payment-check-message">{checkError}</p>
          ) : !check ? (
            <p className="payment-check-message">Loading check...</p>
          ) : (
            <PaymentForm
              subtotal={subtotal}
              tax={tax}
              total={total}
              taxRateLabel={taxRateLabel}
              paymentMethod={paymentMethod}
              onPaymentMethod={setPaymentMethod}
              tipPercent={tipPercent}
              onTipPercent={handleTipPercentChange}
              tipAmount={tipAmount}
              onTipAmount={handleTipAmountChange}
              grandTotal={grandTotal}
              isProcessing={isProcessing}
              onSubmit={handleSubmit}
              onCancel={onClose}
            />
          )}
        </div>
      </div>
    </div>
  );
}

export default PaymentModal;