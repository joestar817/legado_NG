import sourceEditor from '../views/SourceEditor.vue'

export const sourceRoutes = [
  {
    path: '/bookSource',
    name: 'book-home',
    component: sourceEditor,
  },
  {
    path: '/rssSource',
    name: 'rss-home',
    component: sourceEditor,
  },
]
