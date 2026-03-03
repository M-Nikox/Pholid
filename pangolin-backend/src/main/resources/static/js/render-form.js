/* Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 * Pangolin Render Form Handler
 * Handles form submission, validation, and file size checks
 */

const renderForm = (() => {
    let form, submitBtn, fileInput;
    const MAX_FILE_SIZE_MB = 512;
    const MAX_FILE_SIZE_BYTES = 512 * 1024 * 1024;

    let expectedFrames = 0;

    /*
     * Initialize form handlers
     */
    function init() {
        form = document.getElementById('renderForm');
        submitBtn = form.querySelector('button[type="submit"]');
        fileInput = document.getElementById('blendFile');
        
        // Listen for reset event from modal-handlers.js
        document.addEventListener('pangolin:resetForm', reset);

        // Submit button gold flash animation
        submitBtn.addEventListener('click', () => {
            submitBtn.classList.add('clicked');
            setTimeout(() => submitBtn.classList.remove('clicked'), 250);
        });

        // Frame input validation
        setupFrameInputValidation();

        // File input validation
        fileInput.addEventListener('change', validateFile);

        // Drag and drop
        setupDropZone();

        // Form submission
        form.addEventListener('submit', handleSubmit);

        // Compute mode — warn if OptiX selected on Windows/WSL2
        setupComputeModeWarning();
    }

    let checkComputeWarning = () => {}; // updated once setupComputeModeWarning runs

    function setupComputeModeWarning() {
        const select = document.getElementById('computeMode');
        const warning = document.getElementById('optixWarning');
        if (!select || !warning) return;

        const isWindows = /windows/i.test(navigator.userAgent);

        function checkWarning() {
            const isOptix = select.value === 'gpu-optix';
            warning.style.display = (isWindows && isOptix) ? 'block' : 'none';
            submitBtn.disabled = (isWindows && isOptix);
            if (isWindows && isOptix) {
                submitBtn.title = 'OptiX is not supported on Windows/WSL2. Select CUDA or CPU.';
            } else {
                submitBtn.title = '';
            }
        }

        select.addEventListener('change', checkWarning);
        checkWarning(); // run on load in case OptiX is somehow pre-selected on Windows
        checkComputeWarning = checkWarning; // expose to other functions
    }

    /**
     * Setup frame input field validation
     */
    function setupFrameInputValidation() {
        const frameInput = document.getElementById('frames');
        let validationTimeout;

        // Real-time validation feedback (debounced)
        frameInput.addEventListener('input', (e) => {
            const value = e.target.value.trim();
            
            clearTimeout(validationTimeout);
            e.target.setCustomValidity('');
            
            if (value) {
                validationTimeout = setTimeout(() => {
                    const validation = utils.validateFrameInput(value);
                    
                    if (!validation.valid) {
                        e.target.setCustomValidity(validation.error);
                        e.target.reportValidity();
                        console.warn('Frame validation failed:', validation.details);
                    }
                }, 800);
            }
        });

        // Keydown validation - prevent invalid characters
        frameInput.addEventListener("keydown", (e) => {
            const allowedKeys = [
                "Backspace", "Delete", "ArrowLeft", "ArrowRight", "Tab", "Home", "End"
            ];

            if (allowedKeys.includes(e.key)) return;
            if (/\d/.test(e.key)) {
                const futureValue = e.target.value + e.key;
                if (futureValue.length > 15) {
                    e.preventDefault();
                    e.target.setCustomValidity('Frame number too long (max 15 digits)');
                    e.target.reportValidity();
                    return;
                }
                return;
            }

            if (e.key === "-") {
                if (e.target.value.includes("-") || e.target.selectionStart === 0) {
                    e.preventDefault();
                    return;
                }
                return;
            }

            e.preventDefault();
        });

        // Paste validation
        frameInput.addEventListener("paste", (e) => {
            const paste = (e.clipboardData || window.clipboardData).getData("text");
            const proposed =
                e.target.value.slice(0, e.target.selectionStart) +
                paste +
                e.target.value.slice(e.target.selectionEnd);

            if (!/^\d+(-\d+)?$/.test(proposed)) {
                e.preventDefault();
                return;
            }
            
            const validation = utils.validateFrameInput(proposed);
            if (!validation.valid) {
                e.preventDefault();
                e.target.setCustomValidity(validation.error);
                e.target.reportValidity();
            }
        });
    }

    /**
     * Setup drag and drop on the file drop zone
     */
    function setupDropZone() {
        const dropZone = document.getElementById('dropZone');
        if (!dropZone) return;

        dropZone.addEventListener('dragover', (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.add('drag-over');
        });

        dropZone.addEventListener('dragleave', (e) => {
            // Only remove if leaving the dropzone entirely, not entering a child
            if (!dropZone.contains(e.relatedTarget)) {
                dropZone.classList.remove('drag-over');
            }
        });

        dropZone.addEventListener('drop', (e) => {
            e.preventDefault();
            e.stopPropagation();
            dropZone.classList.remove('drag-over');

            const files = e.dataTransfer.files;
            if (!files || files.length === 0) return;

            // Only take the first file
            const file = files[0];

            // Assign to the hidden file input via DataTransfer
            const dt = new DataTransfer();
            dt.items.add(file);
            fileInput.files = dt.files;

            // Trigger the existing validation logic
            fileInput.dispatchEvent(new Event('change'));
        });
    }

    /**
     * Validate uploaded file type and size
     */
    function validateFile(e) {
        const file = e.target.files[0];
        
        // Remove any existing error messages
        const existingError = fileInput.parentElement.querySelector('.file-error');
        if (existingError) existingError.remove();

        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');
        
        if (!file) {
            if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .blend file here';
            if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';
            return;
        }
        
        if (fileNameDisplay) fileNameDisplay.textContent = file.name;
        if (fileSizeDisplay) fileSizeDisplay.textContent = utils.formatFileSize(file.size);

        // 1. CHECK FILE EXTENSION (.blend)
        // We use toLowerCase() to catch .BLEND or .Blend variations
        if (!file.name.toLowerCase().endsWith('.blend')) {
            showFileError(`⚠️ Invalid file type! Please upload a .blend file.`);
            
            fileInput.value = '';
            if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .blend file here';
            if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';
            
            fileInput.setCustomValidity('Invalid file type! Only .blend files are allowed.');
            fileInput.reportValidity();
            
            submitBtn.disabled = true;
            return false;
        }

        // 2. CHECK FILE SIZE
        if (file.size > MAX_FILE_SIZE_BYTES) {
            const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2);
            showFileError(`⚠️ File too large! Maximum size is ${MAX_FILE_SIZE_MB}MB (Your file: ${fileSizeMB}MB)`);
            
            fileInput.value = '';
            if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .blend file here';
            if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';
            
            fileInput.setCustomValidity(`File too large! Maximum ${MAX_FILE_SIZE_MB}MB. Your file: ${fileSizeMB}MB`);
            fileInput.reportValidity();
            
            submitBtn.disabled = true;
            return false;
        }

        // If we get here, the file is valid
        fileInput.setCustomValidity('');
        submitBtn.disabled = false;
        checkComputeWarning(); // re-apply compute mode block if active
        return true;
    }

    /**
     * Helper to show file validation errors
     */
    function showFileError(message) {
        const error = document.createElement('div');
        error.className = 'file-error'; // Renamed from file-size-error to be generic
        error.style.color = '#dc3545'; // Bootstrap danger color or custom red
        error.style.marginTop = '5px';
        error.style.fontSize = '0.9em';
        error.textContent = message;
        fileInput.parentElement.appendChild(error);
    }

    /**
     * Handle form submission
     */
    async function handleSubmit(e) {
        e.preventDefault();

        // Hard guard — never allow OptiX submission on Windows regardless of button state
        const computeSelect = document.getElementById('computeMode');
        if (computeSelect && computeSelect.value === 'gpu-optix' && /windows/i.test(navigator.userAgent)) {
            checkComputeWarning();
            return;
        }

        // Show loading state immediately
        const originalButtonText = submitBtn.textContent;
        submitBtn.disabled = true;
        submitBtn.textContent = 'Uploading...';

        const frameInput = document.getElementById('frames');
        const frameInputValue = frameInput.value;

        // Comprehensive frame validation
        const validation = utils.validateFrameInput(frameInputValue);
        
        if (!validation.valid) {
            frameInput.setCustomValidity(validation.error);
            frameInput.reportValidity();
            resetButtonState(originalButtonText);
            console.error('Frame validation failed:', validation.details);
            return;
        }
        
        frameInput.setCustomValidity('');
        expectedFrames = validation.details.totalFrames;

        // --- FINAL FILE CHECKS ---
        const file = fileInput.files[0];
        
        // Check if file exists
        if (!file) {
             fileInput.setCustomValidity('Please select a .blend file.');
             fileInput.reportValidity();
             resetButtonState(originalButtonText);
             return;
        }

        // Check extension again (Safety check)
        if (!file.name.toLowerCase().endsWith('.blend')) {
            fileInput.setCustomValidity('Invalid file type! Only .blend files are allowed.');
            fileInput.reportValidity();
            resetButtonState(originalButtonText);
            return;
        }

        // Check size again
        if (file.size > MAX_FILE_SIZE_BYTES) {
            const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2);
            fileInput.setCustomValidity(`File too large! Maximum ${MAX_FILE_SIZE_MB}MB. Your file: ${fileSizeMB}MB`);
            fileInput.reportValidity();
            resetButtonState(originalButtonText);
            return;
        }

        // Prepare UI for upload — button already says 'Uploading...'

        // Submit to backend
        const formData = new FormData(form);
        
        try {
            const response = await fetch('/submit', { 
                method: 'POST', 
                body: formData 
            });
            
            const data = await response.json();
            
            if (response.ok && data.jobId) {
                const jobId = data.jobId;
                const projectName = document.getElementById('projectName').value;
                
                // Fire events for active-sessions and notifications modules
                document.dispatchEvent(new CustomEvent('pangolin:startPolling', {
                    detail: { jobId, expectedFrames, projectName }
                }));
                document.dispatchEvent(new CustomEvent('pangolin:jobSubmitted'));

                // Auto-reset the form so it's ready for the next job
                reset();

                // Show success feedback on the button after reset, then restore
                submitBtn.textContent = '✅ Submitted!';
                submitBtn.style.background = '#16a34a';
                submitBtn.disabled = true;
                setTimeout(() => {
                    submitBtn.style.background = '';
                    submitBtn.textContent = 'Launch Render Session';
                    submitBtn.disabled = false;
                }, 2500);

            } else {
                const errorMsg = data.error || data.message || 'Submission failed';
                submitBtn.textContent = '⚠ ' + errorMsg;
                setTimeout(() => resetButtonState(originalButtonText), 5000);
            }
        } catch (err) {
            submitBtn.textContent = '⚠ Upload failed';
            setTimeout(() => resetButtonState(originalButtonText), 5000);
        }
    }

    function resetButtonState(text) {
        submitBtn.disabled = false;
        submitBtn.textContent = text;
    }

    /**
     * Reset form to initial state
     */
    function reset() {
        form.reset();

        // Reset file label
        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');
        if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .blend file here';
        if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';

        // Remove any file error messages
        const existingError = fileInput.parentElement.querySelector('.file-error');
        if (existingError) existingError.remove();

        fileInput.setCustomValidity('');
        expectedFrames = 0;
        submitBtn.disabled = false;
        submitBtn.textContent = 'Launch Render Session';
    }

    return { init, reset };
})();

document.addEventListener('DOMContentLoaded', renderForm.init);