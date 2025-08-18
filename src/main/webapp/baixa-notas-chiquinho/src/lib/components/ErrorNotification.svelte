<script>
	import { logStore } from '$lib/stores/logs.js';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';

	let lastError = $state(null);
	let isConnected = $state(false);
	let showNotification = $state(false);
	let notificationTimeout = null;
	let previousErrorId = null; // Track the previous error ID to detect new ones

	// Subscribe to log store - separated into individual reactive statements
	$effect(() => {
		return logStore.subscribe(state => {
			// Update connection status
			isConnected = state.isConnected;

			// Check for new errors
			if (state.lastError && state.lastError.id !== previousErrorId) {
				lastError = state.lastError;
				previousErrorId = state.lastError.id;
				showErrorNotification();
			} else if (!state.lastError) {
				lastError = null;
				previousErrorId = null;
			}
		});
	});

	function showErrorNotification() {
		showNotification = true;
	}

	function hideNotification() {
		showNotification = false;
	}

	function dismissError() {
		hideNotification();
		logStore.clearLastError();
	}

	// Use SvelteKit's goto instead of window.location.href
	function viewLogs() {
		hideNotification();
		goto('/logs');
	}

	function formatTimestamp(timestamp) {
		return new Date(timestamp).toLocaleTimeString('pt-BR', {
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit'
		});
	}

	// Clean up timeout on destroy
	onMount(() => {
		return () => {
			if (notificationTimeout) {
				clearTimeout(notificationTimeout);
			}
		};
	});
</script>

{#if showNotification && lastError}
	<div class="error-notification" class:visible={showNotification}>
		<div class="notification-content">
			<div class="notification-header">
				<div class="error-icon">🚨</div>
				<div class="error-title">
					<strong>Erro Detectado</strong>
					<span class="error-time">
						{formatTimestamp(lastError.timestamp)}
					</span>
				</div>
				<button class="close-btn" onclick={dismissError} title="Fechar">
					✕
				</button>
			</div>
			
			<div class="error-message">
				{lastError.message}
			</div>
			
			<div class="notification-actions">
				<button class="btn-secondary" onclick={dismissError}>
					Dispensar
				</button>
				<button class="btn-primary" onclick={viewLogs}>
					Ver Logs
				</button>
			</div>
		</div>
	</div>
{/if}

<style>
	.error-notification {
		position: fixed;
		bottom: 1rem;
		right: 1rem;
		z-index: 9999;
		max-width: 400px;
		background: white;
		border: 2px solid #ef4444;
		border-radius: 8px;
		box-shadow: 0 10px 25px rgba(239, 68, 68, 0.15);
		transform: translateX(calc(100% + 2rem));
		transition: transform 0.3s ease-in-out;
	}

	.error-notification.visible {
		transform: translateX(0);
	}

	.notification-content {
		padding: 1rem;
	}

	.notification-header {
		display: flex;
		align-items: flex-start;
		gap: 0.75rem;
		margin-bottom: 0.75rem;
	}

	.error-icon {
		font-size: 1.25rem;
		flex-shrink: 0;
	}

	.error-title {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}

	.error-title strong {
		color: #dc2626;
		font-size: 1rem;
	}

	.error-time {
		font-size: 0.75rem;
		color: #6b7280;
	}

	.close-btn {
		background: none;
		border: none;
		font-size: 1.25rem;
		color: #6b7280;
		cursor: pointer;
		padding: 0;
		width: 24px;
		height: 24px;
		display: flex;
		align-items: center;
		justify-content: center;
		border-radius: 4px;
		transition: background-color 0.2s;
	}

	.close-btn:hover {
		background: #f3f4f6;
		color: #374151;
	}

	.error-message {
		background: #fef2f2;
		border: 1px solid #fecaca;
		border-radius: 4px;
		padding: 0.75rem;
		font-size: 0.875rem;
		line-height: 1.4;
		color: #374151;
		margin-bottom: 1rem;
		max-height: 100px;
		overflow-y: auto;
	}

	.notification-actions {
		display: flex;
		gap: 0.5rem;
		justify-content: flex-end;
	}

	.btn-primary, .btn-secondary {
		padding: 0.5rem 1rem;
		border: none;
		border-radius: 4px;
		font-size: 0.875rem;
		font-weight: 500;
		cursor: pointer;
		transition: background-color 0.2s ease;
	}

	.btn-primary {
		background: #dc2626;
		color: white;
	}

	.btn-primary:hover {
		background: #b91c1c;
	}

	.btn-secondary {
		background: #f3f4f6;
		color: #374151;
		border: 1px solid #d1d5db;
	}

	.btn-secondary:hover {
		background: #e5e7eb;
	}

	/* Mobile responsiveness */
	@media (max-width: 640px) {
		.error-notification {
			left: 1rem;
			right: 1rem;
			max-width: none;
			transform: translateY(-100vh);
		}

		.error-notification.visible {
			transform: translateY(0);
		}
	}
</style>