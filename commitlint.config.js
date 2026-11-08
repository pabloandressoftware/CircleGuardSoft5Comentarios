// HU-02: valida Conventional Commits (feat, fix, docs, ci, chore, test, refactor...).
module.exports = {
  extends: ['@commitlint/config-conventional'],
  rules: {
    // Los asuntos van en español y suelen empezar con siglas o nombres propios
    // (URLs, JaCoCo, QR, SonarQube), que la regla por defecto rechaza.
    'subject-case': [0],
  },
};
