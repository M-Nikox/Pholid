// SPDX-License-Identifier: GPL-3.0-or-later

const JOB_TYPE = {
  label: 'Cycles OPTIX GPU',
  description:
    'OPTIX GPU rendering + extra checkboxes for some experimental features + extra CLI args for Blender',
  settings: [
    // Settings for artists to determine:
    {
      key: 'frames',
      type: 'string',
      required: true,
      eval: "f'{C.scene.frame_start}-{C.scene.frame_end}'",
      evalInfo: {
        showLinkButton: true,
        description: 'Scene frame range',
      },
      description: "Frame range to render. Examples: '47', '1-30', '3, 5-10, 47-327'",
    },
    {
      key: 'chunk_size',
      type: 'int32',
      default: 1,
      description: 'Number of frames to render in one Blender render task',
      visible: 'submission',
    },

    // render_output_root + add_path_components determine the value of render_output_path.
    {
      key: 'render_output_root',
      type: 'string',
      subtype: 'dir_path',
      required: true,
      visible: 'submission',
      description:
        'Base directory of where render output is stored. Will have some job-specific parts appended to it',
    },
    {
      key: 'add_path_components',
      type: 'int32',
      required: true,
      default: 0,
      propargs: { min: 0, max: 32 },
      visible: 'submission',
      description:
        'Number of path components of the current blend file to use in the render output path',
    },
    {
      key: 'render_output_path',
      type: 'string',
      subtype: 'file_path',
      editable: false,
      eval: "str(Path(abspath(settings.render_output_root), last_n_dir_parts(settings.add_path_components), jobname, '{timestamp}', '######'))",
      description: 'Final file path of where render output will be saved',
    },

    {
      key: 'experimental_gp3',
      label: 'Experimental: GPv3',
      description: 'Experimental Flag: Grease Pencil 3',
      type: 'bool',
      required: false,
    },
    {
      key: 'experimental_new_anim',
      label: 'Experimental: Baklava',
      description: 'Experimental Flag: New Animation Data-block',
      type: 'bool',
      required: false,
    },

    // Extra CLI arguments for Blender, for debugging purposes.
    {
      key: 'blender_args_before',
      label: 'Blender CLI args: Before',
      description: 'CLI arguments for Blender, placed before the .blend filename',
      type: 'string',
      required: false,
    },
    {
      key: 'blender_args_after',
      label: 'After',
      description: 'CLI arguments for Blender, placed after the .blend filename',
      type: 'string',
      required: false,
    },

    // Automatically evaluated settings:
    {
      key: 'blendfile',
      type: 'string',
      required: true,
      description: 'Path of the Blend file to render',
      visible: 'web',
    },
    {
      key: 'fps',
      type: 'float',
      eval: 'C.scene.render.fps / C.scene.render.fps_base',
      visible: 'hidden',
    },
    {
      key: 'format',
      type: 'string',
      required: true,
      eval: 'C.scene.render.image_settings.file_format',
      visible: 'web',
    },
    {
      key: 'image_file_extension',
      type: 'string',
      required: true,
      eval: 'C.scene.render.file_extension',
      visible: 'hidden',
      description: 'File extension used when rendering images',
    },
    {
      key: 'has_previews',
      type: 'bool',
      required: false,
      eval: 'C.scene.render.image_settings.use_preview',
      visible: 'hidden',
      description: 'Whether Blender will render preview images.',
    },
  ],
};

// Set of scene.render.image_settings.file_format values that produce
// files which FFmpeg is known not to handle as input.
const ffmpegIncompatibleImageFormats = new Set([
  'EXR',
  'MULTILAYER', // Old CLI-style format indicators
  'OPEN_EXR',
  'OPEN_EXR_MULTILAYER', // DNA values for these formats.
]);

// File formats that would cause rendering to video.
// This is not supported by this job type.
const videoFormats = ['FFMPEG', 'AVI_RAW', 'AVI_JPEG'];

function compileJob(job) {
  print('Blender Render job submitted');
  print('job: ', job);

  const settings = job.settings;
  if (videoFormats.indexOf(settings.format) >= 0) {
    throw `This job type only renders images, and not "${settings.format}"`;
  }

  const renderOutput = renderOutputPath(job);

  // Make sure that when the job is investigated later, it shows the
  // actually-used render output:
  settings.render_output_path = renderOutput;

  const renderDir = path.dirname(renderOutput);
  const renderTasks = authorRenderTasks(settings, renderDir, renderOutput);
  const videoTask = authorCreateVideoTask(settings, renderDir);

  for (const rt of renderTasks) {
    job.addTask(rt);
  }
  if (videoTask) {
    // If there is a video task, all other tasks have to be done first.
    for (const rt of renderTasks) {
      videoTask.addDependency(rt);
    }
    job.addTask(videoTask);
  }

  cleanupJobSettings(job.settings);
}

// Do field replacement on the render output path.
function renderOutputPath(job) {
  let path = job.settings.render_output_path;
  if (!path) {
    throw 'no render_output_path setting!';
  }
  return path.replace(/{([^}]+)}/g, (match, group0) => {
    switch (group0) {
      case 'timestamp':
        return formatTimestampLocal(job.created);
      default:
        return match;
    }
  });
}

const enable_all_optix = `
import bpy

cycles_prefs = bpy.context.preferences.addons['cycles'].preferences
cycles_prefs.compute_device_type = 'OPTIX'
for dev in cycles_prefs.get_devices_for_type('OPTIX'):
    dev.use = (dev.type != 'CPU')
`;

const enable_experimental_common = `
import bpy

exp_prefs = bpy.context.preferences.experimental
`;

function authorRenderTasks(settings, renderDir, renderOutput) {
  print('authorRenderTasks(', renderDir, renderOutput, ')');

  // Extra arguments for Blender.
  const blender_args_before = shellSplit(settings.blender_args_before);
  const blender_args_after = shellSplit(settings.blender_args_after);

  // More arguments for Blender, which will be the same for each task.
  const task_invariant_args = [
    '--python-expr',
    enable_all_optix,
    '--python-expr',
    "import bpy; bpy.context.scene.cycles.device = 'GPU'",
    '--render-output',
    path.join(renderDir, path.basename(renderOutput)),
    '--render-format',
    settings.format,
  ].concat(blender_args_after);

  // Add any experimental flags.
  {
    let py_code_to_join = [enable_experimental_common];
    if (settings.experimental_gp3) {
      py_code_to_join.push('exp_prefs.use_grease_pencil_version3 = True');
    }
    if (settings.experimental_new_anim) {
      py_code_to_join.push('exp_prefs.use_animation_baklava = True');
    }

    // If it's not just the common code, at least one flag was enabled.
    if (py_code_to_join.length > 1) {
      task_invariant_args.push('--python-expr');
      task_invariant_args.push(py_code_to_join.join('\n'));
    }
  }

  // Construct a task for each chunk.
  let renderTasks = [];
  let chunks = frameChunker(settings.frames, settings.chunk_size);
  for (let chunk of chunks) {
    const task = author.Task(`render-${chunk}`, 'blender');
    const command = author.Command('blender-render', {
      exe: '{blender}',
      exeArgs: '{blenderArgs}',
      argsBefore: blender_args_before,
      blendfile: settings.blendfile,
      args: task_invariant_args.concat([
        '--render-frame',
        chunk.replaceAll('-', '..'), // Convert to Blender frame range notation.
      ]),
    });
    task.addCommand(command);
    renderTasks.push(task);
  }
  return renderTasks;
}

function authorCreateVideoTask(settings, renderDir) {
  const needsPreviews = ffmpegIncompatibleImageFormats.has(settings.format);
  if (needsPreviews && !settings.has_previews) {
    print('Not authoring video task, FFmpeg-incompatible render output');
    return;
  }
  if (!settings.fps) {
    print('Not authoring video task, no FPS known:', settings);
    return;
  }

  const stem = path.stem(settings.blendfile).replace('.flamenco', '');
  const outfile = path.join(renderDir, `${stem}-${settings.frames}.mp4`);
  const outfileExt = needsPreviews ? '.jpg' : settings.image_file_extension;

  const task = author.Task('preview-video', 'ffmpeg');
  const command = author.Command('frames-to-video', {
    exe: 'ffmpeg',
    fps: settings.fps,
    inputGlob: path.join(renderDir, `*${outfileExt}`),
    outputFile: outfile,
    args: [
      '-c:v',
      'h264',
      '-crf',
      '20',
      '-g',
      '18',
      '-vf',
      'pad=ceil(iw/2)*2:ceil(ih/2)*2',
      '-pix_fmt',
      'yuv420p',
      '-r',
      settings.fps,
      '-y', // Be sure to always pass either "-n" or "-y".
    ],
  });
  task.addCommand(command);

  print(`Creating output video for ${settings.format}`);
  return task;
}

// Clean up empty job settings so that they're no longer shown in the web UI.
function cleanupJobSettings(settings) {
  const settings_to_check = [
    'blender_args_before',
    'blender_args_after',
    'experimental_gp3',
    'experimental_new_anim',
  ];

  for (let setting_name of settings_to_check) {
    if (!settings[setting_name]) delete settings[setting_name];
  }
}
