import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';

/**
 * Lint rules: mistakes a program can spot by reading the code, before it runs.
 * "npm run lint" checks every file and fails on any warning, and CI runs it on
 * every pull request.
 *
 * WHY THIS FILE EXISTS: Create React App came with its own rule set and checked
 * the code during "npm run build". Vite does not lint at all, so the rules are
 * written out here. They are chosen to match what Create React App enforced:
 * ESLint's own recommended set, the two React hooks rules, and the extra rules
 * Create React App switched on. The aim of the move was the same safety net,
 * not a stricter one, so no screen had to change to pass.
 *
 * HOW TO READ IT: the file exports a list. Each entry says which files it is
 * for ("files") and what applies to them. Later entries add to earlier ones.
 * A rule is 'error' or 'warn'; both fail "npm run lint" (--max-warnings 0).
 */
export default [
  // Never linted: the built app and installed packages
  { ignores: ['build/**', 'node_modules/**'] },

  // ESLint's own recommended rules: undefined names, unreachable code, duplicate keys, ...
  js.configs.recommended,

  // Every source file
  {
    files: ['**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      parserOptions: { ecmaFeatures: { jsx: true } },      // Understand <NavBar /> syntax
      globals: globals.browser                              // window, document, fetch, ... exist
    },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      // The two rules of hooks. A hook called inside an "if" or a loop breaks React;
      // a value used inside useEffect but missing from its list means the effect keeps
      // using an old copy of it.
      'react-hooks/rules-of-hooks': 'error',
      'react-hooks/exhaustive-deps': 'warn',

      // An unused variable or import is usually a leftover. Unused function parameters
      // are allowed (args: 'none'): a callback often has to accept arguments it ignores.
      'no-unused-vars': ['warn', { args: 'none', caughtErrors: 'none', ignoreRestSiblings: true }],

      // ('no-case-declarations' was off here until step N4: Financials had one "case" that
      // declared a variable without braces. That switch moved to
      // components/PeriodPicker/periods.js with the braces added, so the rule is on again,
      // as part of ESLint's recommended set above.)

      // The rest are the extras Create React App switched on
      'array-callback-return': 'warn',                      // A map()/filter() callback that forgets to return
      'eqeqeq': ['warn', 'smart'],                          // === instead of ==, except when comparing with null
      'no-array-constructor': 'warn',
      'no-caller': 'warn',
      'no-eval': 'warn',
      'no-extend-native': 'warn',
      'no-extra-bind': 'warn',
      'no-implied-eval': 'warn',
      'no-iterator': 'warn',
      'no-label-var': 'warn',
      'no-labels': ['warn', { allowLoop: true, allowSwitch: false }],
      'no-lone-blocks': 'warn',
      'no-loop-func': 'warn',
      'no-multi-str': 'warn',
      'no-new-func': 'warn',
      'no-new-wrappers': 'warn',
      'no-object-constructor': 'warn',
      'no-octal-escape': 'warn',
      'no-script-url': 'warn',
      'no-self-compare': 'warn',
      'no-sequences': 'warn',
      'no-template-curly-in-string': 'warn',                // '${name}' in ordinary quotes, where backticks were meant
      'no-throw-literal': 'warn',
      'no-unused-expressions': ['error', { allowShortCircuit: true, allowTernary: true, allowTaggedTemplates: true }],
      'no-use-before-define': ['warn', { functions: false, classes: false, variables: false }],
      'no-useless-computed-key': 'warn',
      'no-useless-concat': 'warn',
      'no-useless-constructor': 'warn',
      'no-useless-rename': 'warn'
    }
  },

  // Test files, and the file that runs before them: Vitest's test, expect, vi, ... exist
  // without being imported (globals: true in vite.config.js), and so does Node's "global"
  {
    files: ['src/**/*.test.{js,jsx}', 'src/setupTests.js'],
    languageOptions: {
      globals: { ...globals.vitest, global: 'writable' }
    }
  },

  // The tools' own settings files run in Node, not in a browser: "process" exists there
  {
    files: ['vite.config.js', 'eslint.config.js'],
    languageOptions: { globals: globals.node }
  }
];
