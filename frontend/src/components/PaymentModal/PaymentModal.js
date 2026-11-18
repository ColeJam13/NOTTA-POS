import { useState, useEffect } from 'react';
import { Banknote, CreditCard, Gift } from 'lucide-react';
import './PaymentModal.css';
import { apiFetch, getJson } from '../../services/api';

/**
 * The pop-up for closing a check: shows what is owed, takes a tip and a
 * payment method, and records the payment.
 *
 * The amounts come from the backend (GET /api/orders/{id}/check), not from the
 * items on the order screen. The backend adds up the items that were actually
 * saved and sent, so the number shown here is the number that gets charged.
 * (This used to add up whatever the screen was holding, including items that
 * were never saved.)
 */
function PaymentModal({ order, onClose, onPaymentSuccess }) {
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
  const taxRateLabel = check ? `${+(check.taxRate * 100).toFixed(2)}%` : '';      // 0.03 -> "3%" (the + drops trailing zeros)
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
            <>
              <div className="order-summary">
                <div className="summary-row">
                  <span>Subtotal:</span>
                  <span>${subtotal.toFixed(2)}</span>
                </div>
                <div className="summary-row">
                  <span>Tax ({taxRateLabel}):</span>
                  <span>${tax.toFixed(2)}</span>
                </div>
                <div className="summary-row total">
                  <span>Total:</span>
                  <span>${total.toFixed(2)}</span>
                </div>
              </div>

              <form onSubmit={handleSubmit}>
                <div className="form-group">
                  <label>Payment Method</label>
                  <div className="payment-methods">
                    <button
                      type="button"
                      className={`payment-method-btn ${paymentMethod === 'cash' ? 'active' : ''}`}
                      onClick={() => setPaymentMethod('cash')}
                    >
                      <Banknote size={20} />
                      <span>CASH</span>
                    </button>
                    <button
                      type="button"
                      className={`payment-method-btn ${paymentMethod === 'credit_card' ? 'active' : ''}`}
                      onClick={() => setPaymentMethod('credit_card')}
                    >
                      <CreditCard size={20} />
                      <span>CREDIT</span>
                    </button>
                    <button
                      type="button"
                      className={`payment-method-btn ${paymentMethod === 'debit_card' ? 'active' : ''}`}
                      onClick={() => setPaymentMethod('debit_card')}
                    >
                      <CreditCard size={20} />
                      <span>DEBIT</span>
                    </button>
                    <button
                      type="button"
                      className={`payment-method-btn ${paymentMethod === 'gift_card' ? 'active' : ''}`}
                      onClick={() => setPaymentMethod('gift_card')}
                    >
                      <Gift size={20} />
                      <span>GIFT CARD</span>
                    </button>
                  </div>
                </div>

                <div className="form-group">
                  <label>Tip Amount</label>
                  <div className="tip-buttons">
                    <button type="button" className={`tip-btn ${tipPercent === 15 ? 'active' : ''}`} onClick={() => handleTipPercentChange(15)}>15%</button>
                    <button type="button" className={`tip-btn ${tipPercent === 18 ? 'active' : ''}`} onClick={() => handleTipPercentChange(18)}>18%</button>
                    <button type="button" className={`tip-btn ${tipPercent === 20 ? 'active' : ''}`} onClick={() => handleTipPercentChange(20)}>20%</button>
                    <button type="button" className={`tip-btn ${tipPercent === 25 ? 'active' : ''}`} onClick={() => handleTipPercentChange(25)}>25%</button>
                  </div>
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Custom tip amount"
                    value={tipAmount}
                    onChange={(e) => handleTipAmountChange(e.target.value)}
                    className="tip-input"
                  />
                </div>

                <div className="grand-total">
                  <span>GRAND TOTAL:</span>
                  <span>${grandTotal.toFixed(2)}</span>
                </div>

                <div className="modal-actions">
                  <button type="button" className="btn-cancel" onClick={onClose}>
                    CANCEL
                  </button>
                  <button type="submit" className="btn-process" disabled={isProcessing}>
                    {isProcessing ? 'PROCESSING...' : 'PROCESS PAYMENT'}
                  </button>
                </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export default PaymentModal;