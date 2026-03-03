/* Copyright © 2026 Pangolin - SPDX-License-Identifier: Apache-2.0
 * Pangolin Utility Functions
 * Shared helper functions
 */

const utils = {
    // Match backend limits
    MAX_FRAMES: 3500,
    MAX_FRAME_NUMBER: 100000,
    MIN_FRAME_NUMBER: 1,

    /**
     * Calculate expected number of frames from input string
     * @param {string} frameRange - Frame input (e.g., "1-10" or "5")
     * @returns {number} Total number of frames
     */
    calculateExpectedFrames(frameRange) {
        if (!frameRange) return 0;
        
        const range = frameRange.trim();
        
        // Single frame
        if (!range.includes('-')) {
            return isNaN(Number(range)) ? 0 : 1;
        }
        
        // Frame range
        const parts = range.split('-').map(p => Number(p.trim()));
        return (isNaN(parts[0]) || isNaN(parts[1])) ? 0 : Math.abs(parts[1] - parts[0]) + 1;
    },

    /**
     * Validate frame input format and values
     * @param {string} value - Frame input value
     * @returns {object} { valid: boolean, error: string|null, details: object }
     */
    validateFrameInput(value) {
        const trimmed = value.trim();
        
        // Check format (single number or range)
        if (!/^\d+(-\d+)?$/.test(trimmed)) {
            return {
                valid: false,
                error: 'Use numbers only (e.g., 3 or 1-10)',
                details: { type: 'format' }
            };
        }
        
        // Parse frame numbers
        let startFrame, endFrame, totalFrames;
        
        if (trimmed.includes('-')) {
            // Range: "1-100"
            const parts = trimmed.split('-');
            
            // Try to parse as numbers
            try {
                startFrame = parseInt(parts[0], 10);
                endFrame = parseInt(parts[1], 10);
                
                // Check if numbers are valid integers
                if (!Number.isFinite(startFrame) || !Number.isFinite(endFrame)) {
                    return {
                        valid: false,
                        error: 'Frame numbers are invalid',
                        details: { type: 'invalid_number' }
                    };
                }
                
                // Check for number overflow (JavaScript safe integer limit)
                if (startFrame > Number.MAX_SAFE_INTEGER || endFrame > Number.MAX_SAFE_INTEGER) {
                    return {
                        valid: false,
                        error: 'Frame numbers are too large',
                        details: { type: 'overflow', start: startFrame, end: endFrame }
                    };
                }
                
            } catch (e) {
                return {
                    valid: false,
                    error: 'Invalid frame numbers',
                    details: { type: 'parse_error' }
                };
            }
            
            // Check minimum frame number
            if (startFrame < this.MIN_FRAME_NUMBER) {
                return {
                    valid: false,
                    error: `Frame numbers must be ≥ ${this.MIN_FRAME_NUMBER}`,
                    details: { type: 'below_minimum', frame: startFrame }
                };
            }
            
            // Check maximum frame number
            if (startFrame > this.MAX_FRAME_NUMBER) {
                return {
                    valid: false,
                    error: `Start frame too high (max: ${this.MAX_FRAME_NUMBER.toLocaleString()})`,
                    details: { type: 'above_maximum', frame: startFrame }
                };
            }
            
            if (endFrame > this.MAX_FRAME_NUMBER) {
                return {
                    valid: false,
                    error: `End frame too high (max: ${this.MAX_FRAME_NUMBER.toLocaleString()})`,
                    details: { type: 'above_maximum', frame: endFrame }
                };
            }
            
            // Check range order
            if (startFrame > endFrame) {
                return {
                    valid: false,
                    error: `Start (${startFrame}) must be ≤ end (${endFrame})`,
                    details: { type: 'backwards_range', start: startFrame, end: endFrame }
                };
            }
            
            totalFrames = endFrame - startFrame + 1;
            
        } else {
            // Single frame: "5"
            try {
                startFrame = parseInt(trimmed, 10);
                
                if (!Number.isFinite(startFrame)) {
                    return {
                        valid: false,
                        error: 'Frame number is invalid',
                        details: { type: 'invalid_number' }
                    };
                }
                
                // Check for overflow
                if (startFrame > Number.MAX_SAFE_INTEGER) {
                    return {
                        valid: false,
                        error: 'Frame number is too large',
                        details: { type: 'overflow', frame: startFrame }
                    };
                }
                
            } catch (e) {
                return {
                    valid: false,
                    error: 'Invalid frame number',
                    details: { type: 'parse_error' }
                };
            }
            
            // Check minimum
            if (startFrame < this.MIN_FRAME_NUMBER) {
                return {
                    valid: false,
                    error: `Frame number must be ≥ ${this.MIN_FRAME_NUMBER}`,
                    details: { type: 'below_minimum', frame: startFrame }
                };
            }
            
            // Check maximum
            if (startFrame > this.MAX_FRAME_NUMBER) {
                return {
                    valid: false,
                    error: `Frame number too high (max: ${this.MAX_FRAME_NUMBER.toLocaleString()})`,
                    details: { type: 'above_maximum', frame: startFrame }
                };
            }
            
            endFrame = startFrame;
            totalFrames = 1;
        }
        
        // Check total frames limit
        if (totalFrames > this.MAX_FRAMES) {
            return {
                valid: false,
                error: `Too many frames (max: ${this.MAX_FRAMES.toLocaleString()}, you requested: ${totalFrames.toLocaleString()})`,
                details: { type: 'too_many_frames', requested: totalFrames, max: this.MAX_FRAMES }
            };
        }
        
        // All checks passed!
        return {
            valid: true,
            error: null,
            details: {
                startFrame,
                endFrame,
                totalFrames
            }
        };
    },

    /**
     * Validate frame input format (legacy - kept for backwards compatibility)
     * @param {string} value - Frame input value
     * @returns {boolean} True if valid format
     */
    isValidFrameInput(value) {
        const result = this.validateFrameInput(value);
        return result.valid;
    },

    /**
     * Format file size in human-readable format
     * @param {number} bytes - File size in bytes
     * @returns {string} Formatted size string
     */
    formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        
        return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
    },

    /**
     * Convert megabytes to bytes
     * @param {number} mb - Size in megabytes
     * @returns {number} Size in bytes
     */
    mbToBytes(mb) {
        return mb * 1024 * 1024;
    }
};
