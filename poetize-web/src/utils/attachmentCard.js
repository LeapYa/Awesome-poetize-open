import constant from './constant';

const ATTACHMENT_TITLE = 'poetize-attachment';
const PREVIEWABLE_EXTENSIONS = new Set([
  'pdf',
  'jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'ico',
  'txt', 'md', 'csv', 'json', 'log',
  'mp3', 'wav', 'ogg',
  'mp4', 'webm', 'mov'
]);

function getFileExtension(fileName = '') {
  const cleanName = String(fileName).split('?')[0];
  const dotIndex = cleanName.lastIndexOf('.');
  if (dotIndex === -1 || dotIndex === cleanName.length - 1) {
    return 'FILE';
  }
  return cleanName.substring(dotIndex + 1).toUpperCase();
}

function isDownloadUrl(url = '') {
  const value = String(url);
  return value.includes('/resource/download?');
}

function isProtectedAttachmentUrl(url = '') {
  return /^\/?u\//i.test(String(url));
}

function normalizeProtectedHref(url = '') {
  let resourcePath = String(url).replace(/^\/?u\//i, '');
  if (!/^https?:\/\//i.test(resourcePath) && !resourcePath.startsWith('/')) {
    resourcePath = '/' + resourcePath;
  }
  return resourcePath;
}

function getUrlPathname(url = '') {
  try {
    return new URL(url, window.location.origin).pathname;
  } catch (error) {
    return '';
  }
}

function isLocalArticleFileUrl(url = '') {
  return getUrlPathname(url).startsWith('/static/articleFile/');
}

function buildDownloadUrl(url = '', fileName = '') {
  const resourcePath = getUrlPathname(url);
  if (!resourcePath || !resourcePath.startsWith('/static/articleFile/')) {
    return url;
  }
  const params = new URLSearchParams();
  params.set('path', resourcePath);
  if (fileName) {
    params.set('filename', fileName);
  }
  return `${constant.baseURL}/resource/download?${params.toString()}`;
}

function shouldPreview(extension = '') {
  return PREVIEWABLE_EXTENSIONS.has(String(extension).toLowerCase());
}

export function transformAttachmentLinks(html) {
  if (!html || typeof document === 'undefined') {
    return html || '';
  }

  const wrapper = document.createElement('div');
  wrapper.innerHTML = html;

  wrapper.querySelectorAll('a[href]').forEach((link) => {
    const originalHref = link.getAttribute('href') || '';
    const isAttachment = link.getAttribute('title') === ATTACHMENT_TITLE || isProtectedAttachmentUrl(originalHref);
    if (!isAttachment) {
      return;
    }

    const isPrivate = isProtectedAttachmentUrl(originalHref);
    const name = (link.textContent || '附件').trim() || '附件';
    const href = isPrivate ? normalizeProtectedHref(originalHref) : originalHref;
    const extension = getFileExtension(name);
    const canPreview = shouldPreview(extension);
    const downloadHref = isLocalArticleFileUrl(href) ? buildDownloadUrl(href, name) : href;

    const card = document.createElement('span');
    card.className = 'poetize-attachment-card';
    card.setAttribute('data-poetize-attachment', 'true');
    card.setAttribute('data-poetize-attachment-href', href);
    card.setAttribute('data-poetize-attachment-name', name);
    if (isPrivate) {
      card.setAttribute('data-poetize-private-attachment', 'true');
    }

    const icon = document.createElement('span');
    icon.className = 'poetize-attachment-pill-icon';
    icon.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" stroke="currentColor" stroke-width="2.5" fill="none"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"></path></svg>`;

    const title = document.createElement('span');
    title.className = 'poetize-attachment-pill-name';
    title.textContent = name;

    const meta = document.createElement('span');
    meta.className = 'poetize-attachment-pill-meta';
    meta.textContent = isPrivate ? `${extension} · 登录可见` : extension;

    const actions = document.createElement('span');
    actions.className = 'poetize-attachment-pill-actions';

    if (canPreview) {
      const preview = document.createElement('a');
      preview.className = 'poetize-attachment-pill-btn';
      preview.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path><circle cx="12" cy="12" r="3"></circle></svg>`;
      preview.setAttribute('href', href);
      preview.setAttribute('target', '_blank');
      preview.setAttribute('rel', 'noopener noreferrer');
      preview.setAttribute('data-poetize-attachment-action', 'preview');
      preview.setAttribute('title', '预览');
      actions.appendChild(preview);
    }

    const download = document.createElement('a');
    download.className = 'poetize-attachment-pill-btn';
    download.innerHTML = `<svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" stroke-width="2" fill="none"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>`;
    download.setAttribute('href', downloadHref);
    download.setAttribute('rel', 'noopener noreferrer');
    download.setAttribute('data-poetize-attachment-action', 'download');
    download.setAttribute('title', '下载');
    if (!isDownloadUrl(downloadHref)) {
      download.setAttribute('target', '_blank');
      download.setAttribute('download', name);
    }
    actions.appendChild(download);

    card.appendChild(icon);
    card.appendChild(title);
    card.appendChild(meta);
    card.appendChild(actions);
    link.replaceWith(card);
  });

  return wrapper.innerHTML;
}
