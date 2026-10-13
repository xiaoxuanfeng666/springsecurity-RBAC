<template>
  <div class="page-container">
    <el-card shadow="never" class="welcome-card">
      <div class="welcome-title">
        你好，{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}
      </div>
      <div class="welcome-sub">欢迎使用 RBAC 权限管理后台</div>
    </el-card>

    <el-row :gutter="16" class="info-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>我的角色</span>
          </template>
          <el-tag v-for="role in userStore.roles" :key="role" class="tag-item" type="success">
            {{ role }}
          </el-tag>
          <el-empty v-if="userStore.roles.length === 0" description="暂无角色" :image-size="60" />
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>我的权限标识（{{ userStore.permissions.length }} 个）</span>
          </template>
          <el-tag v-for="perm in userStore.permissions" :key="perm" class="tag-item" type="info">
            {{ perm }}
          </el-tag>
          <el-empty v-if="userStore.permissions.length === 0" description="暂无权限" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="tip-card">
      <template #header>
        <span>功能演示提示</span>
      </template>
      <ul class="tip-list">
        <li><b>无感知续期：</b>Access Token 15 分钟过期，前端自动用 Refresh Token 续期并重放请求，页面无跳转。</li>
        <li><b>离职立刻失效：</b>管理员在「用户管理」禁用某个用户，该用户已登录的会话立即失效。</li>
        <li><b>动态改权限：</b>在「角色管理」修改角色权限后，该角色用户刷新页面菜单与按钮立即变化，无需重新登录。</li>
      </ul>
    </el-card>
  </div>
</template>

<script setup>
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
</script>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}

.welcome-title {
  font-size: 20px;
  font-weight: 600;
}

.welcome-sub {
  margin-top: 8px;
  color: #909399;
}

.info-row {
  margin-bottom: 16px;
}

.tag-item {
  margin: 0 8px 8px 0;
}

.tip-list {
  margin: 0;
  padding-left: 20px;
  line-height: 2;
  color: #606266;
}
</style>
