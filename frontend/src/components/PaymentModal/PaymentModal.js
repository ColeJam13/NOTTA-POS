import { useState } from 'react';
import './PaymentModal.css';

function PaymentModal({ order, orderItems, onClose, onPaymentSuccess }) {
  const [paymentMethod, setPaymentMethod] = useState('credit_card');
  const [tipAmount, setTipAmount] = useState('');
  const [tipPercent, setTipPercent] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  // Calculate order total
  const subtotal = orderItems.reduce((sum, item) => sum + item.price, 0);
  const tax = subtotal * 0.03;
  const total = subtotal + tax;
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
      const response = await fetch('http://localhost:8080/api/payments', {
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
        onPaymentSuccess();
      } else {
        alert('Payment failed. Please try again.');
      }
    } catch (error) {
      console.error('Payment error:', error);
      alert('Payment failed. Please try again.');
    } finally {
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
          <div className="order-summary">
            <div className="summary-row">
              <span>Subtotal:</span>
              <span>${subtotal.toFixed(2)}</span>
            </div>
            <div className="summary-row">
              <span>Tax (3%):</span>
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
                  💵 CASH
                </button>
                <button
                  type="button"
                  className={`payment-method-btn ${paymentMethod === 'credit_card' ? 'active' : ''}`}
                  onClick={() => setPaymentMethod('credit_card')}
                >
                  💳 CREDIT
                </button>
                <button
                  type="button"
                  className={`payment-method-btn ${paymentMethod === 'debit_card' ? 'active' : ''}`}
                  onClick={() => setPaymentMethod('debit_card')}
                >
                  💳 DEBIT
                </button>
                <button
                  type="button"
                  className={`payment-method-btn ${paymentMethod === 'gift_card' ? 'active' : ''}`}
                  onClick={() => setPaymentMethod('gift_card')}
                >
                  🎁 GIFT CARD
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
        </div>
      </div>
    </div>
  );
}

export default PaymentModal;