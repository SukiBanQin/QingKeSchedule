import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/settings')
  await page.evaluate(() => localStorage.clear())
  await page.reload()
})

test('新用户设置学期、创建课程并在刷新后恢复', async ({ page }) => {
  const today = new Date()
  const localDate = [
    today.getFullYear(),
    String(today.getMonth() + 1).padStart(2, '0'),
    String(today.getDate()).padStart(2, '0'),
  ].join('-')
  const dayOfWeek = String(today.getDay() === 0 ? 7 : today.getDay())
  await page.getByLabel('学期名称').fill('2026 秋季学期')
  await page.getByLabel('开始日期').fill(localDate)
  await page.getByRole('button', { name: '保存并添加课程' }).click()

  await expect(page).toHaveURL(/\/courses\/new/)
  await page.getByLabel('课程名称').fill('数据结构')
  await page.getByLabel('任课教师').fill('陈老师')
  await page.getByLabel('星期').selectOption(dayOfWeek)
  await page.getByLabel('教室').fill('博学楼 A201')
  await page.getByRole('button', { name: '保存课程' }).click()

  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByRole('button', { name: /编辑 数据结构/ })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: /编辑 数据结构/ })).toBeVisible()

  await page.getByRole('link', { name: '今日课程' }).click()
  await expect(page.getByRole('heading', { name: '数据结构' })).toBeVisible()
})

test('单周课程只在单周显示', async ({ page }) => {
  await page.getByRole('button', { name: '保存并添加课程' }).click()
  await page.getByLabel('课程名称').fill('大学英语')
  await page.getByLabel('重复规则').selectOption('odd')
  await page.getByRole('button', { name: '保存课程' }).click()

  await expect(page.getByRole('button', { name: /编辑 大学英语/ })).toBeVisible()
  await page.getByRole('button', { name: '下一周' }).click()
  await expect(page.getByRole('button', { name: /编辑 大学英语/ })).toHaveCount(0)
})

test('冲突课程提示后仍可保存，并支持编辑与删除', async ({ page }) => {
  await page.getByRole('button', { name: '保存并添加课程' }).click()
  await page.getByLabel('课程名称').fill('数据结构')
  await page.getByRole('button', { name: '保存课程' }).click()

  await page.goto('/courses/new')
  await page.getByLabel('课程名称').fill('操作系统')
  await page.getByRole('button', { name: '保存课程' }).click()
  await expect(page.getByRole('alertdialog')).toContainText('检测到时间冲突')
  await page.getByRole('button', { name: '仍然保存' }).click()

  await expect(page.getByText('冲突').first()).toBeVisible()
  await page.getByRole('button', { name: /编辑 操作系统/ }).click()
  await page.getByLabel('课程名称').fill('操作系统导论')
  await page.getByRole('button', { name: '保存修改' }).click()
  await page.getByRole('button', { name: '仍然保存' }).click()
  await expect(page.getByRole('button', { name: /编辑 操作系统导论/ })).toBeVisible()

  await page.getByRole('button', { name: /编辑 操作系统导论/ }).click()
  await page.getByRole('button', { name: '删除课程' }).click()
  await page.getByRole('button', { name: '确认删除' }).click()
  await expect(page.getByRole('button', { name: /编辑 操作系统导论/ })).toHaveCount(0)
})
