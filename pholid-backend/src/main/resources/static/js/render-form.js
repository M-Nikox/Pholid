/* Copyright © 2026 Pholid - SPDX-License-Identifier: Apache-2.0
 * Pholid Render Form Handler
 * Handles form submission, validation, and file size checks
 */

const renderForm = (() => {
    let form, submitBtn, fileInput;
    const MAX_FILE_SIZE_MB = 512;
    const MAX_FILE_SIZE_BYTES = 512 * 1024 * 1024;

    let expectedFrames = 0;
    let fileMode = null; // 'blend' | 'zip' | null

    /*
     * Initialize form handlers
     */
    function init() {
        form = document.getElementById('renderForm');
        submitBtn = form.querySelector('button[type="submit"]');
        fileInput = document.getElementById('blendFile');
        
        // Listen for reset event from modal-handlers.js
        document.addEventListener('pholid:resetForm', reset);

        // Mode selector buttons
        const selectBlendMode = document.getElementById('selectBlendMode');
        const selectZipMode   = document.getElementById('selectZipMode');
        const backToModeBtn   = document.getElementById('backToModeBtn');

        if (selectBlendMode) selectBlendMode.addEventListener('click', () => setFileMode('blend'));
        if (selectZipMode)   selectZipMode.addEventListener('click',   () => setFileMode('zip'));
        if (backToModeBtn)   backToModeBtn.addEventListener('click',   () => setFileMode(null));

        // Clear file button — only visible when a file is loaded
        const clearFileBtn = document.getElementById('clearFileBtn');
        if (clearFileBtn) {
            clearFileBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation(); // prevent label reopening the file picker
                clearFile();
            });
        }

        // Clear form button
        const clearFormBtn = document.getElementById('clearFormBtn');
        if (clearFormBtn) {
            clearFormBtn.addEventListener('click', () => reset());
        }

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

        // Compute mode: warn if OptiX selected on Windows/WSL2
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

        clearDropZoneError();

        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');

        if (!file) {
            const defaultText = fileMode === 'zip' ? 'Drop your .zip file here' : 'Drop your .blend file here';
            if (fileNameDisplay) fileNameDisplay.textContent = defaultText;
            if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';
            return;
        }

        if (fileNameDisplay) fileNameDisplay.textContent = file.name;
        if (fileSizeDisplay) fileSizeDisplay.textContent = utils.formatFileSize(file.size);

        // 1. CHECK FILE EXTENSION (mode-aware)
        const expectedExt = fileMode === 'zip' ? '.zip' : '.blend';
        if (!file.name.toLowerCase().endsWith(expectedExt)) {
            fileInput.value = '';
            showFileError(`⚠️ Wrong file type! Please upload a ${expectedExt} file.`);
            fileInput.setCustomValidity(`Only ${expectedExt} files are allowed in this mode.`);
            submitBtn.disabled = true;
            return false;
        }

        // 2. CHECK FILE SIZE
        if (file.size > MAX_FILE_SIZE_BYTES) {
            const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2);
            fileInput.value = '';
            showFileError(`⚠️ File too large! Maximum size is ${MAX_FILE_SIZE_MB}MB (Your file: ${fileSizeMB}MB)`);
            fileInput.setCustomValidity(`File too large! Maximum ${MAX_FILE_SIZE_MB}MB. Your file: ${fileSizeMB}MB`);
            submitBtn.disabled = true;
            return false;
        }

        // File is valid
        fileInput.setCustomValidity('');
        submitBtn.disabled = false;
        setClearFileBtn(true);
        checkComputeWarning();
        return true;
    }

    /**
     * Switch between null (mode selector), 'blend', and 'zip' modes.
     * Controls which UI sections are visible and configures the file input.
     */
    function setFileMode(mode) {
        fileMode = mode;

        const selector    = document.getElementById('fileModeSelector');
        const uploadArea  = document.getElementById('fileUploadArea');
        const blendNameRow = document.getElementById('blendFileNameRow');
        const dropZoneIcon = document.getElementById('dropZoneIcon');
        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');

        if (mode === null) {
            // Back to mode selection
            if (selector)   selector.style.display   = '';
            if (uploadArea) uploadArea.style.display  = 'none';
            clearFile();
            return;
        }

        // Show upload area, hide selector
        if (selector)   selector.style.display   = 'none';
        if (uploadArea) uploadArea.style.display  = '';

        if (mode === 'zip') {
            fileInput.accept = '.zip';
            if (blendNameRow)   blendNameRow.style.display  = '';
            if (dropZoneIcon)   dropZoneIcon.textContent     = '🗂️';
            if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .zip project here';
        } else {
            fileInput.accept = '.blend';
            if (blendNameRow)   blendNameRow.style.display  = 'none';
            if (dropZoneIcon)   dropZoneIcon.textContent     = '🎨';
            if (fileNameDisplay) fileNameDisplay.textContent = 'Drop your .blend file here';
        }

        if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';
        clearDropZoneError();
        setClearFileBtn(false);
    }

    /**
     * Show non-blocking backend warnings (e.g. absolute paths detected in zip).
     * Displayed in orange below the drop zone, auto-dismisses after 10s.
     */
    function showSubmitWarnings(warnings) {
        // Remove any existing warning banner
        const existing = document.getElementById('submitWarningBanner');
        if (existing) existing.remove();

        const banner = document.createElement('div');
        banner.id = 'submitWarningBanner';
        banner.style.cssText = [
            'background:rgba(var(--pholid-orange-rgb),0.1)',
            'border:1px solid rgba(var(--pholid-orange-rgb),0.35)',
            'border-radius:10px',
            'padding:10px 14px',
            'font-size:0.8rem',
            'font-weight:600',
            'color:var(--pholid-orange)',
            'margin-top:8px',
            'line-height:1.5',
        ].join(';');

        banner.textContent = '⚠ ' + warnings.join(' ');

        const uploadArea = document.getElementById('fileUploadArea');
        const submitRow  = document.querySelector('#renderForm .flex.gap-3');
        const anchor     = uploadArea || submitRow;
        if (anchor) anchor.insertAdjacentElement('afterend', banner);

        setTimeout(() => banner.remove(), 10000);
    }

    /**
     * Show a file validation error using the drop zone itself — no layout shift.
     */
    function showFileError(message) {
        setClearFileBtn(false);
        const dropZone = document.getElementById('dropZone');
        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');
        if (!dropZone) return;

        dropZone.style.borderColor = 'rgba(239, 68, 68, 0.6)';
        dropZone.style.background  = 'rgba(239, 68, 68, 0.04)';

        if (fileNameDisplay) {
            fileNameDisplay.textContent   = message;
            fileNameDisplay.style.color   = '#ef4444';
            fileNameDisplay.style.opacity = '1';
        }
        if (fileSizeDisplay) {
            fileSizeDisplay.textContent   = 'Drop a file or click to browse';
            fileSizeDisplay.style.color   = '#ef4444';
            fileSizeDisplay.style.opacity = '0.7';
        }
    }

    /**
     * Reset the drop zone to its default appearance.
     */
    function clearDropZoneError() {
        const dropZone = document.getElementById('dropZone');
        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');
        if (!dropZone) return;

        dropZone.style.borderColor = '';
        dropZone.style.background  = '';

        if (fileNameDisplay) {
            fileNameDisplay.style.color   = '';
            fileNameDisplay.style.opacity = '';
        }
        if (fileSizeDisplay) {
            fileSizeDisplay.style.color   = '';
            fileSizeDisplay.style.opacity = '';
        }
    }

    /**
     * Show or hide the clear file button inside the drop zone.
     */
    function setClearFileBtn(visible) {
        const btn = document.getElementById('clearFileBtn');
        if (!btn) return;
        btn.style.display = visible ? 'flex' : 'none';
    }

    /**
     * Handle form submission
     */
    async function handleSubmit(e) {
        e.preventDefault();

        // Hard guard: never allow OptiX submission on Windows regardless of button state
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
            const label = fileMode === 'zip' ? '.zip' : '.blend';
            showFileError(`⚠️ Please select a ${label} file before submitting.`);
            resetButtonState(originalButtonText);
            return;
        }

        // Zip mode: require blendFileName
        if (fileMode === 'zip') {
            const blendFileNameInput = document.getElementById('blendFileName');
            if (!blendFileNameInput || !blendFileNameInput.value.trim()) {
                blendFileNameInput.focus();
                resetButtonState(originalButtonText);
                return;
            }
        }

        // Safety: re-check extension and size
        const expectedExt = fileMode === 'zip' ? '.zip' : '.blend';
        if (!file.name.toLowerCase().endsWith(expectedExt)) {
            showFileError(`⚠️ Wrong file type! Expected a ${expectedExt} file.`);
            resetButtonState(originalButtonText);
            return;
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            const fileSizeMB = (file.size / (1024 * 1024)).toFixed(2);
            showFileError(`⚠️ File too large! Maximum ${MAX_FILE_SIZE_MB}MB. Your file: ${fileSizeMB}MB`);
            resetButtonState(originalButtonText);
            return;
        }

        // Submit to backend
        const formData = new FormData(form);

        try {
            const response = await fetch('/api/render/submit', {
                method: 'POST',
                body: formData
            });

            const data = await response.json();

            if (response.ok && data.jobId) {
                const jobId = data.jobId;
                const projectName = document.getElementById('projectName').value;

                // Fire events for active-sessions and notifications modules
                document.dispatchEvent(new CustomEvent('pholid:startPolling', {
                    detail: { jobId, expectedFrames, projectName }
                }));
                document.dispatchEvent(new CustomEvent('pholid:jobSubmitted'));

                // Show warnings before resetting if the backend found absolute paths
                if (data.warnings && data.warnings.length > 0) {
                    showSubmitWarnings(data.warnings);
                }

                // Auto-reset the form so it's ready for the next job
                reset();

                // Show success feedback on the button after reset, then restore
                submitBtn.textContent = '✅ Submitted!';
                submitBtn.classList.add('btn-success');
                submitBtn.disabled = true;
                setTimeout(() => {
                    submitBtn.classList.remove('btn-success');
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
     * Clear only the blend file, restoring the drop zone to its default state.
     */
    function clearFile() {
        fileInput.value = '';
        fileInput.setCustomValidity('');

        // Reset drop zone text to the correct default for the current mode
        const fileNameDisplay = document.getElementById('fileNameDisplay');
        const fileSizeDisplay = document.getElementById('fileSizeDisplay');
        if (fileNameDisplay) {
            fileNameDisplay.textContent = fileMode === 'zip'
                ? 'Drop your .zip project here'
                : 'Drop your .blend file here';
        }
        if (fileSizeDisplay) fileSizeDisplay.textContent = 'Max 512MB';

        clearDropZoneError();
        setClearFileBtn(false);
        submitBtn.disabled = false;
    }

    /**
     * Reset form to initial state, returning to the mode selector.
     */
    function reset() {
        form.reset();

        fileInput.setCustomValidity('');
        expectedFrames = 0;
        submitBtn.disabled = false;
        submitBtn.textContent = 'Launch Render Session';

        // Return to mode selector — setFileMode(null) handles all display cleanup
        setFileMode(null);

        // Remove any warning banners
        const banner = document.getElementById('submitWarningBanner');
        if (banner) banner.remove();
    }

    return { init, reset };
})();

document.addEventListener('DOMContentLoaded', renderForm.init);
/* ===== CUSTOM DROPDOWN INIT ===== */
document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.pholid-dropdown').forEach(dropdown => {
        const targetId = dropdown.dataset.target;
        const select   = document.getElementById(targetId);
        const btn      = dropdown.querySelector('.pholid-dropdown-btn');
        const label    = dropdown.querySelector('.pholid-dropdown-label');
        const options  = dropdown.querySelectorAll('.pholid-dropdown-option');

        // Toggle open
        btn.addEventListener('click', (e) => {
            e.stopPropagation();
            // Close all other dropdowns first
            document.querySelectorAll('.pholid-dropdown.open').forEach(d => {
                if (d !== dropdown) d.classList.remove('open');
            });
            dropdown.classList.toggle('open');
        });

        // Select option
        options.forEach(option => {
            option.addEventListener('click', () => {
                const value = option.dataset.value;
                const text  = option.textContent;

                // Update label
                label.textContent = text;

                // Sync hidden select so FormData and getElementById().value work
                select.value = value;
                select.dispatchEvent(new Event('change', { bubbles: true }));

                // Update selected state
                options.forEach(o => o.classList.remove('selected'));
                option.classList.add('selected');

                dropdown.classList.remove('open');
            });
        });
    });

    // Close dropdowns when clicking outside
    document.addEventListener('click', () => {
        document.querySelectorAll('.pholid-dropdown.open').forEach(d => d.classList.remove('open'));
    });
});
