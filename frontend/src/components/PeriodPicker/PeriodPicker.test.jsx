import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PeriodPicker, { periodOf } from './index';

/**
 * Tests for the period buttons shared by Financials and the leaderboard.
 *
 * The component holds nothing itself, so most tests hand it a period and look at what
 * it reports. One test puts it in a small screen that does hold the period, the way
 * the real screens use it.
 */

test('the four periods are buttons, and the chosen one is lit', () => {
  render(<PeriodPicker period={periodOf('week')} onChange={() => {}} />);

  expect(screen.getAllByRole('button').map(button => button.textContent)).toEqual(['TODAY', 'THIS WEEK', 'THIS MONTH', 'CUSTOM']);
  expect([...document.querySelectorAll('.period-btn.active')].map(button => button.textContent)).toEqual(['THIS WEEK']);
});

test('pressing a button reports the new kind and keeps the dates that were picked', async () => {
  const onChange = vi.fn();
  render(<PeriodPicker period={{ kind: 'custom', customStart: '2026-03-05', customEnd: '2026-03-07' }} onChange={onChange} />);

  await userEvent.click(screen.getByRole('button', { name: 'TODAY' }));

  expect(onChange).toHaveBeenCalledWith({ kind: 'today', customStart: '2026-03-05', customEnd: '2026-03-07' });
});

test('the date boxes are there for a custom period only', () => {
  const { rerender } = render(<PeriodPicker period={periodOf('month')} onChange={() => {}} />);
  expect(document.querySelectorAll('input[type="date"]')).toHaveLength(0);

  rerender(<PeriodPicker period={periodOf('custom')} onChange={() => {}} />);
  expect(document.querySelectorAll('input[type="date"]')).toHaveLength(2);
});

test('each date box is tied to its label, and reports the date picked in it', () => {
  const onChange = vi.fn();
  render(<PeriodPicker period={{ kind: 'custom', customStart: '2026-03-05', customEnd: '' }} onChange={onChange} />);

  // getByLabelText finds a box through its label, as a screen reader does
  expect(screen.getByLabelText('Start Date:')).toHaveValue('2026-03-05');
  expect(screen.getByLabelText('End Date:')).toHaveValue('');

  fireEvent.change(screen.getByLabelText('End Date:'), { target: { value: '2026-03-07' } });
  expect(onChange).toHaveBeenCalledWith({ kind: 'custom', customStart: '2026-03-05', customEnd: '2026-03-07' });
});

test('in a screen that keeps the period, the dates survive a visit to another period', async () => {
  function AScreen() {
    const [period, setPeriod] = useState(periodOf('today'));
    return <PeriodPicker period={period} onChange={setPeriod} />;
  }
  render(<AScreen />);

  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));
  fireEvent.change(screen.getByLabelText('Start Date:'), { target: { value: '2026-03-05' } });
  await userEvent.click(screen.getByRole('button', { name: 'THIS MONTH' }));
  await userEvent.click(screen.getByRole('button', { name: 'CUSTOM' }));

  expect(screen.getByLabelText('Start Date:')).toHaveValue('2026-03-05');
});
