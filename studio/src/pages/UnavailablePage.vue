<script setup lang="ts">
function retry() { window.location.assign('/') }
</script>

<template>
  <section class="auth-card">
    <p class="eyebrow">RED FRAMEWORK</p>
    <h1>Server unavailable</h1>
    <p>Studio cannot reach RED Server. Check the server and PostgreSQL connection, then retry.</p>
    <button type="button" @click="retry">Retry connection</button>
  </section>
</template>
