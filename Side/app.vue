<script setup lang="ts">
useHead({
  title: 'Job Tracker',
  meta: [{ name: 'description', content: 'Personal job application tracker' }],
})
</script>

<template>
  <!-- UApp provides Nuxt UI's toast/overlay portals and theming. -->
  <UApp>
    <NuxtPage />
  </UApp>
</template>
