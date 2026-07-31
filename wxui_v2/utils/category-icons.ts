export interface CategoryWithIcon extends Category {
  icon: string
}

const CATEGORY_ICON_FALLBACK = '/static/category-icons/other.svg'
const CATEGORY_ICON_BY_NAME: Record<string, string> = {
  二手书: '/static/category-icons/books.svg',
  日常用品: '/static/category-icons/daily.svg',
  学习用品: '/static/category-icons/study.svg',
  数码产品: '/static/category-icons/digital.svg',
  电子配件: '/static/category-icons/accessories.svg',
  体育运动: '/static/category-icons/sports.svg',
  食品零食: '/static/category-icons/snacks.svg',
  代步工具: '/static/category-icons/transport.svg',
  其他: CATEGORY_ICON_FALLBACK
}

export const RECOMMEND_CATEGORY: CategoryWithIcon = {
  id: '',
  name: '推荐',
  icon: '/static/category-icons/recommend.svg'
}

export function withCategoryIcon(category: Category): CategoryWithIcon {
  return {
    ...category,
    icon: CATEGORY_ICON_BY_NAME[category.name] || CATEGORY_ICON_FALLBACK
  }
}
