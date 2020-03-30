module.exports = {
  title: 'Apache Pinot™ (Incubating)',
  tagline: 'Realtime distributed OLAP datastore',
  url: 'https://pinot.apache.com',
  baseUrl: '/',
  favicon: 'img/favicon.ico',
  organizationName: 'apache',
  projectName: 'pinot', 
  themeConfig: {
    navbar: {
      hideOnScroll: true,
      title: 'Pinot™ (Incubating)',
      logo: {
        alt: 'Pinot',
        src: 'img/logo.svg',
      },
      links: [
        {to: 'https://docs.pinot.apache.org/', label: 'Docs', position: 'right'},
        {to: '/download', label: 'Download', position: 'right'},
        {to: 'https://docs.pinot.apache.org/community-1/blogs', label: 'Blog', position: 'right'},
        {
          href: 'https://github.com/apache/incubator-pinot',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    prism: {
      theme: require('prism-react-renderer/themes/github'),
      darkTheme: require('prism-react-renderer/themes/dracula'),
    },
    footer: {
      style: 'light',
      links: [
        {
          title: 'About',
          items: [
            {
              label: 'What is Pinot?',
              to: 'https://docs.pinot.apache.org/',
            },
            {
              label: 'Components',
              to: 'https://docs.pinot.apache.org/pinot-components',
            },
            {
              label: 'Architecture',
              to: 'https://docs.pinot.apache.org/concepts/architecture',
            },
            {
              label: 'PluginsArchitecture',
              to: 'https://docs.pinot.apache.org/plugins/plugin-architecture',
            },
          ],
        },
        {
          title: 'Components',
          items: [
            {
              label: 'Presto',
              to: 'https://docs.pinot.apache.org/integrations/presto',
            },
            {
              label: 'Superset',
              to: 'https://docs.pinot.apache.org/integrations/superset',
            },
            {
              label: 'ThirdEye',
              to: 'https://docs.pinot.apache.org/integrations/thirdeye',
            }
          ],
        },
        {
          title: 'Docs',
          items: [
            {
              label: 'GettingStarted',
              to: 'https://docs.pinot.apache.org/getting-started',
            },
            {
              label: 'PinotComponents',
              to: 'https://docs.pinot.apache.org/pinot-components',
            },
            {
              label: 'UserGuide',
              to: 'https://docs.pinot.apache.org/pinot-user-guide',
            },
            {
              label: 'Administration',
              to: 'https://docs.pinot.apache.org/operating-pinot',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'Slack',
              to: 'https://communityinviter.com/apps/apache-pinot/apache-pinot',
            },
            {
              label: 'Github',
              to: 'https://github.com/apache/incubator-pinot',
            },
            {
              label: 'Twitter',
              to: 'https://twitter.com/ApachePinot',
            },
            {
              label: 'Mailing List',
              to: 'mailto:dev-subscribe@pinot.apache.org?Subject=SubscribeToPinot',
            },
          ],
        },
      ],
      logo: {
        alt: 'Apache Pinot™ - Incubating',
        src: 'img/logo.svg',
        href: 'https://pinot.apache.org/',
      },
      copyright: `Copyright © ${new Date().getFullYear()} The Apache Software Foundation.`,
    },
    googleAnalytics: {
      trackingID: 'UA-157446650-1',
    },
    algolia: {
      apiKey: 'f3cde09979e469ad62eaea4e115c21ea',
      indexName: 'apache_pinot',
      algoliaOptions: {}, // Optional, if provided by Algolia
    },
  },
  plugins: [
    [
      '@docusaurus/plugin-ideal-image',
      {
        quality: 70,
        max: 1030, // max resized image's size.
        min: 640, // min resized image's size. if original is lower, use that size.
        steps: 2, // the max number of images generated between min and max (inclusive)
      },
    ],
  ],
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          editUrl: 'https://github.com/apache/incubator-pinot/edit/master/website/',
          // Sidebars filepath relative to the website dir.
          sidebarPath: require.resolve('./sidebars.js'),
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
  scripts: [],
  stylesheets: [
    'https://fonts.googleapis.com/css?family=Ubuntu|Roboto|Source+Code+Pro',
    'https://at-ui.github.io/feather-font/css/iconfont.css',
  ],
};
