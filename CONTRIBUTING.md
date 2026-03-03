# Contributing to Pangolin

Thank you for your interest in contributing to Pangolin!  

This document outlines the guidelines for contributing, reporting issues, and submitting improvements. Even though this is primarily a personal project, contributions are welcome.

---

## Reporting Issues

- Please use GitHub Issues for bug reports or feature requests.
- Include a **clear description**, steps to reproduce, and any relevant logs or screenshots.
- Label issues appropriately when possible (e.g., bug, enhancement).

---

## Submitting Changes

1. **Fork the repository**  
2. **Create a new branch** for your change:  
   ```bash
   git checkout -b feature/my-change
   ```
3. **Make your changes**
4. **Run any necessary tests** to ensure your changes don't break functionality.
5. **Commit your changes with a clear message**:
    ```bash
    git commit -m "Add feature x with y"
    ```
6. **Push your branch and submit a Pull Request (PR)

## Code Style & Guidelines

- Use consistent formatting for Python, Java (Spring Boot), and Docker files
- Include comments where necessary, especially for architecture or GPU-specific logic
- Keep commits small and focused; one logical change per commit
- Avoid committing sensitive credentials — use .env files and .env.example

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.