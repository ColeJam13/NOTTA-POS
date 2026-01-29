/**
 * Utility functions for formatting data across the application
 * Centralized formatting logic for consistency and maintainability
 */

/**
 * Format table name based on whether it's a Quick Order or regular table
 * @param {Object} table - Table object with tableNumber and isQuickOrder properties
 * @returns {string} - Formatted table name (e.g., "Quick Order QO1" or "Table W3")
 */
export const formatTableName = (table) => {
    if (!table || !table.tableNumber) return 'Unknown';
    
    // Check if it's a Quick Order (either by flag or by table number starting with "QO")
    const isQuickOrder = table.isQuickOrder || table.tableNumber.startsWith('QO');
    
    if (isQuickOrder) {
        return `Quick Order ${table.tableNumber}`;
    }
    
    return `Table ${table.tableNumber}`;
};

/**
 * Format timestamp as relative time (e.g., "5m ago", "2h 15m ago", "3d ago")
 * @param {string|number|Date} timestamp - Timestamp to format
 * @returns {string} - Formatted relative time string
 */
export const formatTimeAgo = (timestamp) => {
    const now = Date.now();
    const created = new Date(timestamp).getTime();
    const diff = Math.floor((now - created) / 1000 / 60); // minutes
    
    if (diff < 60) return `${diff}m ago`;
    
    const hours = Math.floor(diff / 60);
    if (hours < 24) return `${hours}h ${diff % 60}m ago`;
    
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
};

/**
 * Format timestamp as date and time (e.g., "01/29/2026 3:45 PM")
 * @param {string|number|Date} timestamp - Timestamp to format
 * @returns {string} - Formatted date/time string or 'N/A' if invalid
 */
export const formatDateTime = (timestamp) => {
    if (!timestamp) return 'N/A';
    
    const date = new Date(timestamp);
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const year = date.getFullYear();
    let hours = date.getHours();
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const ampm = hours >= 12 ? 'PM' : 'AM';
    hours = hours % 12 || 12;
    
    return `${month}/${day}/${year} ${hours}:${minutes} ${ampm}`;
};

/**
 * Format payment method for display (e.g., "CREDIT CARD" from "credit_card")
 * @param {string} method - Payment method string
 * @returns {string} - Formatted payment method in uppercase with spaces
 */
export const formatPaymentMethod = (method) => {
    if (!method) return 'UNKNOWN';
    return method.replace(/_/g, ' ').toUpperCase();
};