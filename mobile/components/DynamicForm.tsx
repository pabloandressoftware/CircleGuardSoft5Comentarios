import React from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { QuestionInput, QuestionType } from './QuestionInput';

interface Question {
  id: string;
  text: string;
  type: QuestionType;
  options?: string;
}

interface DynamicFormProps {
  questions: Question[];
  responses: Record<string, any>;
  onAnswer: (id: string, value: any) => void;
}

export const DynamicForm: React.FC<DynamicFormProps> = ({ questions, responses, onAnswer }) => {
  return (
    <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
      {questions.map((q) => (
        <QuestionInput
          key={q.id}
          question={q}
          value={responses[q.id]}
          onChange={(val) => onAnswer(q.id, val)}
        />
      ))}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
