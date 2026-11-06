import React, { useState } from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { DynamicForm } from '../DynamicForm';

const mockQuestions: any[] = [
  { id: '1', text: 'Question 1', type: 'YES_NO' },
  { id: '2', text: 'Question 2', type: 'TEXT' }
];

function FormWithResponses() {
  const [responses, setResponses] = useState<Record<string, any>>({});
  return (
    <DynamicForm
      questions={mockQuestions}
      responses={responses}
      onAnswer={(id, value) => setResponses(previous => ({ ...previous, [id]: value }))}
    />
  );
}

describe('DynamicForm', () => {
  it('renders all questions', () => {
    const { getByText } = render(
      <FormWithResponses />
    );

    expect(getByText('Question 1')).toBeTruthy();
    expect(getByText('Question 2')).toBeTruthy();
  });

  it('updates text responses', () => {
    const { getByPlaceholderText } = render(
      <FormWithResponses />
    );

    const input = getByPlaceholderText('Type your response...');
    fireEvent.changeText(input, 'Testing');
    
    expect(input.props.value).toBe('Testing');
  });
});
