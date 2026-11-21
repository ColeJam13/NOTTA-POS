import { Banknote, CreditCard, Gift } from 'lucide-react';
import './PaymentModal.css';

/**
 * The inside of the Payment window: the check (subtotal, tax, total), the payment
 * method, the tip, the grand total, and the two buttons.
 *
 * It only draws what it is given and reports what is pressed. The numbers, the
 * arithmetic, and the talking to the backend all stay in PaymentModal.
 *
 * In its own file so that the template gallery can draw the very same form the
 * Payment window does (views/Gallery). Styles come from PaymentModal.css.
 *
 * @param {number} subtotal, tax, total - The check, as the backend worked it out
 * @param {string} taxRateLabel - The rate as shown beside "Tax", e.g. "3%"
 * @param {string} paymentMethod - 'cash', 'credit_card', 'debit_card', or 'gift_card'
 * @param {Function} onPaymentMethod - Called with a method when its button is pressed
 * @param {number|null} tipPercent - The percent button that is lit, if any
 * @param {Function} onTipPercent - Called with 15, 18, 20, or 25
 * @param {string} tipAmount - What is in the custom tip box
 * @param {Function} onTipAmount - Called with the text typed into the custom tip box
 * @param {number} grandTotal - Total plus tip
 * @param {boolean} isProcessing - The payment is being sent: the button says so and is off
 * @param {Function} onSubmit - Called when PROCESS PAYMENT is pressed
 * @param {Function} onCancel - Called when CANCEL is pressed
 */
function PaymentForm({
  subtotal, tax, total, taxRateLabel,
  paymentMethod, onPaymentMethod,
  tipPercent, onTipPercent, tipAmount, onTipAmount,
  grandTotal, isProcessing, onSubmit, onCancel
}) {
  return (
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

      <form onSubmit={onSubmit}>
        <div className="form-group">
          <label>Payment Method</label>
          <div className="payment-methods">
            <button
              type="button"
              className={`payment-method-btn ${paymentMethod === 'cash' ? 'active' : ''}`}
              onClick={() => onPaymentMethod('cash')}
            >
              <Banknote size={20} />
              <span>CASH</span>
            </button>
            <button
              type="button"
              className={`payment-method-btn ${paymentMethod === 'credit_card' ? 'active' : ''}`}
              onClick={() => onPaymentMethod('credit_card')}
            >
              <CreditCard size={20} />
              <span>CREDIT</span>
            </button>
            <button
              type="button"
              className={`payment-method-btn ${paymentMethod === 'debit_card' ? 'active' : ''}`}
              onClick={() => onPaymentMethod('debit_card')}
            >
              <CreditCard size={20} />
              <span>DEBIT</span>
            </button>
            <button
              type="button"
              className={`payment-method-btn ${paymentMethod === 'gift_card' ? 'active' : ''}`}
              onClick={() => onPaymentMethod('gift_card')}
            >
              <Gift size={20} />
              <span>GIFT CARD</span>
            </button>
          </div>
        </div>

        <div className="form-group">
          <label>Tip Amount</label>
          <div className="tip-buttons">
            <button type="button" className={`tip-btn ${tipPercent === 15 ? 'active' : ''}`} onClick={() => onTipPercent(15)}>15%</button>
            <button type="button" className={`tip-btn ${tipPercent === 18 ? 'active' : ''}`} onClick={() => onTipPercent(18)}>18%</button>
            <button type="button" className={`tip-btn ${tipPercent === 20 ? 'active' : ''}`} onClick={() => onTipPercent(20)}>20%</button>
            <button type="button" className={`tip-btn ${tipPercent === 25 ? 'active' : ''}`} onClick={() => onTipPercent(25)}>25%</button>
          </div>
          <input
            type="number"
            step="0.01"
            min="0"
            placeholder="Custom tip amount"
            value={tipAmount}
            onChange={(e) => onTipAmount(e.target.value)}
            className="tip-input"
          />
        </div>

        <div className="grand-total">
          <span>GRAND TOTAL:</span>
          <span>${grandTotal.toFixed(2)}</span>
        </div>

        <div className="modal-actions">
          <button type="button" className="btn-cancel" onClick={onCancel}>
            CANCEL
          </button>
          <button type="submit" className="btn-process" disabled={isProcessing}>
            {isProcessing ? 'PROCESSING...' : 'PROCESS PAYMENT'}
          </button>
        </div>
      </form>
    </>
  );
}

export default PaymentForm;
