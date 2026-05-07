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

function protectAttachmentUrl(url = '') {
  if (isProtectedAttachmentUrl(url)) {
    return url;
  }
  const value = String(url);
  if (/^https?:\/\//i.test(value)) {
    return value;
  }
  return `u/${value.replace(/^\/+/, '')}`;
}

export function createAttachmentMarkdown(label, url, options = {}) {
  const href = options.privateAttachment ? protectAttachmentUrl(url) : url;
  return `[${label}](${href} "${ATTACHMENT_TITLE}")\n`;
}

export function isAttachmentTitle(title) {
  return title === ATTACHMENT_TITLE;
}

export function transformAttachmentLinks(html) {
  if (!html || typeof document === 'undefined') {
    return html || '';
  }

  const wrapper = document.createElement('div');
  wrapper.innerHTML = html;
  transformAttachmentLinksInElement(wrapper);
  return wrapper.innerHTML;
}

export function transformAttachmentLinksInElement(root) {
  if (!root || typeof root.querySelectorAll !== 'function') {
    return;
  }

  root.querySelectorAll('a[href]').forEach((link) => {
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
    icon.className = 'poetize-attachment-icon';
    icon.textContent = extension.slice(0, 4);

    const body = document.createElement('span');
    body.className = 'poetize-attachment-body';

    const title = document.createElement('span');
    title.className = 'poetize-attachment-name';
    title.textContent = name;

    const meta = document.createElement('span');
    meta.className = 'poetize-attachment-meta';
    meta.textContent = isPrivate ? `${extension} · 登录后可访问` : extension;

    const actions = document.createElement('span');
    actions.className = 'poetize-attachment-actions';

    if (canPreview) {
      const preview = document.createElement('a');
      preview.className = 'poetize-attachment-action-link';
      preview.textContent = '预览';
      preview.setAttribute('href', href);
      preview.setAttribute('target', '_blank');
      preview.setAttribute('rel', 'noopener noreferrer');
      preview.setAttribute('data-poetize-attachment-action', 'preview');
      actions.appendChild(preview);
    }

    const download = document.createElement('a');
    download.className = 'poetize-attachment-action-link';
    download.textContent = '下载';
    download.setAttribute('href', downloadHref);
    download.setAttribute('rel', 'noopener noreferrer');
    download.setAttribute('data-poetize-attachment-action', 'download');
    if (!isDownloadUrl(downloadHref)) {
      download.setAttribute('target', '_blank');
      download.setAttribute('download', name);
    }
    actions.appendChild(download);

    body.appendChild(title);
    body.appendChild(meta);
    card.appendChild(icon);
    card.appendChild(body);
    card.appendChild(actions);
    link.replaceWith(card);
  });
}
